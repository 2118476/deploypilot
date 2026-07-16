package com.deploypilot.service;

import com.deploypilot.dto.ConnectionResponse;
import com.deploypilot.exception.ResourceNotFoundException;
import com.deploypilot.model.ProviderConnection;
import com.deploypilot.model.enums.ConnectionStatus;
import com.deploypilot.model.enums.ConnectionType;
import com.deploypilot.model.enums.ProviderType;
import com.deploypilot.provider.ProviderCredential;
import com.deploypilot.provider.ProviderRegistry;
import com.deploypilot.provider.model.HostingSite;
import com.deploypilot.provider.model.ProviderAccount;
import com.deploypilot.provider.model.RepositorySummary;
import com.deploypilot.repository.ProviderConnectionRepository;
import com.deploypilot.security.CredentialEncryptionService;
import com.deploypilot.util.CurrentUserUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Manages per-user provider connections. Tokens are validated against the
 * provider, stored encrypted and never returned. Every operation is scoped to
 * the current authenticated user.
 */
@Service
public class ConnectionService {

    private final ProviderConnectionRepository repository;
    private final CredentialEncryptionService encryption;
    private final ProviderRegistry providers;

    public ConnectionService(ProviderConnectionRepository repository,
                             CredentialEncryptionService encryption,
                             ProviderRegistry providers) {
        this.repository = repository;
        this.encryption = encryption;
        this.providers = providers;
    }

    /** Status for every provider (connected or not) for the current user. */
    @Transactional(readOnly = true)
    public List<ConnectionResponse> list() {
        Long userId = CurrentUserUtil.getCurrentUserId();
        List<ConnectionResponse> out = new ArrayList<>();
        for (ProviderType provider : ProviderType.values()) {
            Optional<ProviderConnection> conn = repository.findByUserIdAndProvider(userId, provider);
            out.add(conn.map(this::toResponse).orElseGet(() -> disconnected(provider)));
        }
        return out;
    }

    /**
     * Connects (or replaces) a provider connection. The token is validated by a
     * live call to the provider; only on success is it encrypted and stored.
     */
    public ConnectionResponse connect(ProviderType provider, String connectionTypeInput, String token) {
        Long userId = CurrentUserUtil.getCurrentUserId();
        ConnectionType connectionType = resolveType(provider, connectionTypeInput);
        ProviderCredential credential = new ProviderCredential(provider, connectionType, token.trim());

        // Validate against the provider (throws ProviderException on bad credentials).
        ProviderAccount account = validate(provider, credential);

        ProviderConnection connection = repository.findByUserIdAndProvider(userId, provider)
            .orElseGet(ProviderConnection::new);
        connection.setUserId(userId);
        connection.setProvider(provider);
        connection.setConnectionType(connectionType);
        connection.setAccountLabel(account.label());
        connection.setExternalAccountId(account.externalId());
        connection.setScopes(account.scopes());
        connection.setEncryptedCredential(encryption.encrypt(token.trim()));
        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setLastError(null);
        return toResponse(repository.save(connection));
    }

    /** Removes the stored credential. Provider-side revocation is described to the user. */
    public void disconnect(ProviderType provider) {
        Long userId = CurrentUserUtil.getCurrentUserId();
        ProviderConnection connection = repository.findByUserIdAndProvider(userId, provider)
            .orElseThrow(() -> new ResourceNotFoundException("No " + provider + " connection to disconnect"));
        repository.delete(connection);
    }

    /** Repositories the current user's GitHub connection can access. */
    public List<RepositorySummary> listRepositories() {
        Long userId = CurrentUserUtil.getCurrentUserId();
        return providers.git().listRepositories(requireCredential(userId, ProviderType.GITHUB));
    }

    /** Existing sites/services for the current user's connection to the given hosting provider. */
    public List<HostingSite> listSites(ProviderType provider) {
        if (provider == ProviderType.GITHUB) {
            throw new IllegalArgumentException("GitHub does not host sites; use the repository list.");
        }
        Long userId = CurrentUserUtil.getCurrentUserId();
        return providers.hosting(provider).listSites(requireCredential(userId, provider));
    }

    // ---------- internal use by the automation engine ----------

    /** Returns a decrypted credential for the current user, or throws if not connected. */
    @Transactional
    public ProviderCredential requireCredential(Long userId, ProviderType provider) {
        ProviderConnection connection = repository.findByUserIdAndProvider(userId, provider)
            .filter(c -> c.getStatus() == ConnectionStatus.CONNECTED)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Connect your " + provider + " account before running this automation"));
        connection.setLastUsedAt(Instant.now());
        repository.save(connection);
        return new ProviderCredential(provider, connection.getConnectionType(),
            encryption.decrypt(connection.getEncryptedCredential()));
    }

    public Optional<ProviderConnection> findConnection(Long userId, ProviderType provider) {
        return repository.findByUserIdAndProvider(userId, provider);
    }

    // ---------- helpers ----------

    private ProviderAccount validate(ProviderType provider, ProviderCredential credential) {
        if (provider == ProviderType.GITHUB) {
            return providers.git().getAccount(credential);
        }
        return providers.hosting(provider).getAccount(credential);
    }

    private ConnectionType resolveType(ProviderType provider, String input) {
        if (input != null && !input.isBlank()) {
            ConnectionType requested;
            try {
                requested = ConnectionType.valueOf(input.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown connection type: " + input);
            }
            if (!belongsTo(provider, requested)) {
                throw new IllegalArgumentException(requested + " is not a valid connection type for " + provider);
            }
            return requested;
        }
        return switch (provider) {
            case GITHUB -> ConnectionType.GITHUB_PAT;
            case NETLIFY -> ConnectionType.NETLIFY_PAT;
            case RENDER -> ConnectionType.RENDER_API_KEY;
        };
    }

    private boolean belongsTo(ProviderType provider, ConnectionType type) {
        return switch (provider) {
            case GITHUB -> type == ConnectionType.GITHUB_APP || type == ConnectionType.GITHUB_PAT;
            case NETLIFY -> type == ConnectionType.NETLIFY_OAUTH || type == ConnectionType.NETLIFY_PAT;
            case RENDER -> type == ConnectionType.RENDER_API_KEY;
        };
    }

    private ConnectionResponse toResponse(ProviderConnection c) {
        ConnectionResponse r = new ConnectionResponse();
        r.setProvider(c.getProvider().name());
        r.setConnected(c.getStatus() == ConnectionStatus.CONNECTED);
        r.setConnectionType(c.getConnectionType().name());
        r.setAccountLabel(c.getAccountLabel());
        r.setScopes(c.getScopes());
        r.setStatus(c.getStatus().name());
        r.setLastError(c.getLastError());
        r.setConnectedAt(c.getCreatedAt());
        r.setLastUsedAt(c.getLastUsedAt());
        return r;
    }

    private ConnectionResponse disconnected(ProviderType provider) {
        ConnectionResponse r = new ConnectionResponse();
        r.setProvider(provider.name());
        r.setConnected(false);
        r.setStatus("NOT_CONNECTED");
        return r;
    }
}

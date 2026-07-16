package com.deploypilot.model;

import com.deploypilot.model.enums.ConnectionStatus;
import com.deploypilot.model.enums.ConnectionType;
import com.deploypilot.model.enums.ProviderType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * A per-user connection to an external provider. The provider credential is
 * stored only in {@code encryptedCredential} (AES-GCM ciphertext) and is never
 * exposed through any API or log. One connection per user per provider.
 */
@Entity
@Table(name = "provider_connections", uniqueConstraints = {
    @UniqueConstraint(name = "uq_connection_user_provider", columnNames = {"user_id", "provider"})
}, indexes = {
    @Index(name = "idx_connections_user", columnList = "user_id")
})
public class ProviderConnection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Enumerated(EnumType.STRING) @Column(name = "provider", nullable = false, length = 20) private ProviderType provider;
    @Enumerated(EnumType.STRING) @Column(name = "connection_type", nullable = false, length = 30) private ConnectionType connectionType;
    @Column(name = "account_label", length = 200) private String accountLabel;
    @Column(name = "external_account_id", length = 200) private String externalAccountId;
    @Column(name = "scopes", length = 500) private String scopes;
    @Column(name = "encrypted_credential", nullable = false, columnDefinition = "TEXT") private String encryptedCredential;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 20) private ConnectionStatus status = ConnectionStatus.CONNECTED;
    @Column(name = "last_error", length = 300) private String lastError;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "last_used_at") private Instant lastUsedAt;

    public ProviderConnection() {}

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long u) { this.userId = u; }
    public ProviderType getProvider() { return provider; } public void setProvider(ProviderType p) { this.provider = p; }
    public ConnectionType getConnectionType() { return connectionType; } public void setConnectionType(ConnectionType c) { this.connectionType = c; }
    public String getAccountLabel() { return accountLabel; } public void setAccountLabel(String a) { this.accountLabel = a; }
    public String getExternalAccountId() { return externalAccountId; } public void setExternalAccountId(String e) { this.externalAccountId = e; }
    public String getScopes() { return scopes; } public void setScopes(String s) { this.scopes = s; }
    public String getEncryptedCredential() { return encryptedCredential; } public void setEncryptedCredential(String e) { this.encryptedCredential = e; }
    public ConnectionStatus getStatus() { return status; } public void setStatus(ConnectionStatus s) { this.status = s; }
    public String getLastError() { return lastError; } public void setLastError(String l) { this.lastError = l; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant c) { this.createdAt = c; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastUsedAt() { return lastUsedAt; } public void setLastUsedAt(Instant l) { this.lastUsedAt = l; }
}

package com.deploypilot.service;

import com.deploypilot.dto.SecretView;
import com.deploypilot.exception.ResourceNotFoundException;
import com.deploypilot.exception.UnauthorizedAccessException;
import com.deploypilot.model.AutomationSecret;
import com.deploypilot.model.Project;
import com.deploypilot.repository.AutomationSecretRepository;
import com.deploypilot.repository.ProjectRepository;
import com.deploypilot.security.CredentialEncryptionService;
import com.deploypilot.util.CurrentUserUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Stores user-supplied deployment secret values, encrypted at rest and scoped to
 * the owning user/project. Values are write-only: they are never returned after
 * saving. The automation engine reads decrypted values through the internal
 * accessors only; they never reach a controller response or a log.
 */
@Service
public class SecretService {

    private final AutomationSecretRepository repository;
    private final ProjectRepository projectRepository;
    private final CredentialEncryptionService encryption;
    private final SecureRandom random = new SecureRandom();

    public SecretService(AutomationSecretRepository repository,
                         ProjectRepository projectRepository,
                         CredentialEncryptionService encryption) {
        this.repository = repository;
        this.projectRepository = projectRepository;
        this.encryption = encryption;
    }

    @Transactional(readOnly = true)
    public List<SecretView> list(Long projectId) {
        requireOwnedProject(projectId);
        return repository.findByProjectIdOrderByVarNameAsc(projectId).stream().map(this::toView).toList();
    }

    /** Saves or replaces a secret value. Returns the masked view (never the value). */
    public SecretView save(Long projectId, String name, String value, String destination) {
        Project project = requireOwnedProject(projectId);
        AutomationSecret secret = repository.findByProjectIdAndVarName(projectId, name)
            .orElseGet(AutomationSecret::new);
        secret.setUserId(project.getUserId());
        secret.setProjectId(projectId);
        secret.setVarName(name);
        secret.setDestination(destination);
        secret.setEncryptedValue(encryption.encrypt(value));
        return toView(repository.save(secret));
    }

    @Transactional
    public void remove(Long projectId, String name) {
        requireOwnedProject(projectId);
        if (repository.findByProjectIdAndVarName(projectId, name).isEmpty()) {
            throw new ResourceNotFoundException("No stored secret named " + name);
        }
        repository.deleteByProjectIdAndVarName(projectId, name);
    }

    // ---------- internal accessors for the automation engine ----------

    Set<String> storedNames(Long projectId) {
        return repository.findByProjectIdOrderByVarNameAsc(projectId).stream()
            .map(AutomationSecret::getVarName).collect(java.util.stream.Collectors.toSet());
    }

    Optional<String> getValue(Long projectId, String name) {
        return repository.findByProjectIdAndVarName(projectId, name)
            .map(s -> encryption.decrypt(s.getEncryptedValue()));
    }

    /**
     * Returns an existing stored value or generates a new cryptographically strong
     * one, persists it encrypted and returns it. Used for app-owned secrets such
     * as JWT signing keys so redeploys stay stable.
     */
    String getOrGenerate(Long projectId, Long userId, String name) {
        Optional<String> existing = getValue(projectId, name);
        if (existing.isPresent()) return existing.get();
        byte[] buf = new byte[48];
        random.nextBytes(buf);
        String generated = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        AutomationSecret secret = new AutomationSecret();
        secret.setUserId(userId);
        secret.setProjectId(projectId);
        secret.setVarName(name);
        secret.setDestination("Backend service");
        secret.setEncryptedValue(encryption.encrypt(generated));
        repository.save(secret);
        return generated;
    }

    private SecretView toView(AutomationSecret s) {
        SecretView v = new SecretView();
        v.setName(s.getVarName());
        v.setDestination(s.getDestination());
        v.setHasValue(true);
        v.setUpdatedAt(s.getUpdatedAt());
        return v;
    }

    private Project requireOwnedProject(Long projectId) {
        Long userId = CurrentUserUtil.getCurrentUserId();
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        if (!project.getUserId().equals(userId)) {
            throw new UnauthorizedAccessException("Not your project");
        }
        return project;
    }
}

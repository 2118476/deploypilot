package com.deploypilot.service;

import com.deploypilot.exception.ConflictException;
import com.deploypilot.exception.UnauthorizedAccessException;
import com.deploypilot.model.DeploymentConfirmation;
import com.deploypilot.model.enums.AutomationMode;
import com.deploypilot.repository.DeploymentConfirmationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * Issues and validates short-lived, single-use confirmations. A confirmation is
 * bound to the user, project, exact plan hash, repository/commit and provider
 * accounts, and expires. It cannot be replayed once consumed, and it cannot
 * authorise a plan whose hash has changed.
 */
@Service
public class ConfirmationService {

    private final DeploymentConfirmationRepository repository;
    private final SecureRandom random = new SecureRandom();
    private final long ttlSeconds;

    public ConfirmationService(DeploymentConfirmationRepository repository,
                               @Value("${deploypilot.automation.confirmation-ttl-seconds:600}") long ttlSeconds) {
        this.repository = repository;
        this.ttlSeconds = ttlSeconds;
    }

    public DeploymentConfirmation create(Long userId, Long projectId, Long runId, AutomationMode mode,
                                         String planHash, String repository, String commitSha, String accountBinding) {
        DeploymentConfirmation c = new DeploymentConfirmation();
        c.setUserId(userId);
        c.setProjectId(projectId);
        c.setAutomationRunId(runId);
        c.setNonce(newNonce());
        c.setPlanHash(planHash);
        c.setMode(mode);
        c.setRepositoryFullName(repository);
        c.setCommitSha(commitSha);
        c.setAccountBinding(accountBinding);
        c.setExpiresAt(Instant.now().plusSeconds(ttlSeconds));
        return this.repository.save(c);
    }

    /**
     * Validates and consumes a confirmation. Throws with a specific message for
     * replay, expiry, ownership and plan-change failures. On success the
     * confirmation is marked consumed and cannot be used again.
     */
    @Transactional
    public DeploymentConfirmation consume(Long userId, Long projectId, String nonce, String currentPlanHash) {
        DeploymentConfirmation c = repository.findByNonce(nonce)
            .orElseThrow(() -> new ConflictException("Confirmation not found. Review the plan and confirm again."));
        if (!c.getUserId().equals(userId) || !c.getProjectId().equals(projectId)) {
            throw new UnauthorizedAccessException("This confirmation does not belong to you.");
        }
        if (c.getConsumedAt() != null) {
            throw new ConflictException("This confirmation has already been used. Confirm again to run once more.");
        }
        if (Instant.now().isAfter(c.getExpiresAt())) {
            throw new ConflictException("This confirmation has expired. Review the plan and confirm again.");
        }
        if (!c.getPlanHash().equals(currentPlanHash)) {
            throw new ConflictException("The plan changed since you confirmed. Review the new plan and confirm again.");
        }
        c.setConsumedAt(Instant.now());
        return repository.save(c);
    }

    private String newNonce() {
        byte[] buf = new byte[24];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}

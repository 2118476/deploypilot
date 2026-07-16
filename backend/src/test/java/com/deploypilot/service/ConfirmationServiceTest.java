package com.deploypilot.service;

import com.deploypilot.exception.ConflictException;
import com.deploypilot.exception.UnauthorizedAccessException;
import com.deploypilot.model.DeploymentConfirmation;
import com.deploypilot.model.enums.AutomationMode;
import com.deploypilot.repository.DeploymentConfirmationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** Confirmation binding: single-use, expiry, plan-hash match and ownership. */
@SpringBootTest
@ActiveProfiles("test")
class ConfirmationServiceTest {

    @Autowired ConfirmationService service;
    @Autowired DeploymentConfirmationRepository repository;

    private DeploymentConfirmation create(long userId, long projectId, String hash) {
        return service.create(userId, projectId, 1L, AutomationMode.DEPLOY_FOR_ME, hash,
            "demo/sample-monorepo", "commitsha", "GITHUB=42;NETLIFY=nf;RENDER=rd");
    }

    @Test
    void consumesOnceThenRejectsReplay() {
        DeploymentConfirmation c = create(1L, 10L, "hashA");
        DeploymentConfirmation consumed = service.consume(1L, 10L, c.getNonce(), "hashA");
        assertNotNull(consumed.getConsumedAt());
        // Replay must be refused.
        assertThrows(ConflictException.class, () -> service.consume(1L, 10L, c.getNonce(), "hashA"));
    }

    @Test
    void rejectsExpiredConfirmation() {
        DeploymentConfirmation c = create(1L, 11L, "hashA");
        c.setExpiresAt(Instant.now().minusSeconds(5));
        repository.save(c);
        ConflictException e = assertThrows(ConflictException.class,
            () -> service.consume(1L, 11L, c.getNonce(), "hashA"));
        assertTrue(e.getMessage().toLowerCase().contains("expired"));
    }

    @Test
    void rejectsChangedPlanHash() {
        DeploymentConfirmation c = create(1L, 12L, "hashA");
        ConflictException e = assertThrows(ConflictException.class,
            () -> service.consume(1L, 12L, c.getNonce(), "hashB"));
        assertTrue(e.getMessage().toLowerCase().contains("changed"));
        // The confirmation must not have been consumed by a failed attempt.
        assertNull(repository.findByNonce(c.getNonce()).orElseThrow().getConsumedAt());
    }

    @Test
    void rejectsWrongOwner() {
        DeploymentConfirmation c = create(1L, 13L, "hashA");
        assertThrows(UnauthorizedAccessException.class,
            () -> service.consume(2L, 13L, c.getNonce(), "hashA"));
    }

    @Test
    void rejectsUnknownNonce() {
        assertThrows(ConflictException.class, () -> service.consume(1L, 14L, "no-such-nonce", "hashA"));
    }
}

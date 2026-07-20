package com.deploypilot.troubleshoot;

import com.deploypilot.dto.ExecutionStep;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Failed-step selection: the right step is chosen and completed steps are never treated as failed. */
class TroubleshootingContextServiceTest {

    private ExecutionStep step(String id, String status) {
        ExecutionStep s = new ExecutionStep();
        s.setId(id);
        s.setTitle(id);
        s.setStatus(status);
        return s;
    }

    @Test
    void picksTheFailedStepNotACompletedOne() {
        List<ExecutionStep> steps = List.of(
            step("a", "SUCCEEDED"), step("b", "SUCCEEDED"), step("c", "FAILED"), step("d", "PENDING"));
        ExecutionStep picked = TroubleshootingContextService.pickFailedStep(steps, null);
        assertNotNull(picked);
        assertEquals("c", picked.getId(), "the FAILED step must be selected, never a completed one");
    }

    @Test
    void honoursAnExplicitStepId() {
        List<ExecutionStep> steps = List.of(step("a", "SUCCEEDED"), step("c", "FAILED"));
        assertEquals("a", TroubleshootingContextService.pickFailedStep(steps, "a").getId());
    }

    @Test
    void fallsBackToRunningWhenNothingFailed() {
        List<ExecutionStep> steps = List.of(step("a", "SUCCEEDED"), step("b", "RUNNING"), step("c", "PENDING"));
        assertEquals("b", TroubleshootingContextService.pickFailedStep(steps, null).getId());
    }

    @Test
    void returnsNullWhenNothingActionable() {
        List<ExecutionStep> steps = List.of(step("a", "SUCCEEDED"), step("b", "SUCCEEDED"));
        assertNull(TroubleshootingContextService.pickFailedStep(steps, null));
    }

    // ---------- verification-after-restart detection ----------

    private ExecutionStep timed(String id, String type, String status, java.time.Instant started, java.time.Instant finished) {
        ExecutionStep s = step(id, status);
        s.setType(type);
        s.setStartedAt(started);
        s.setFinishedAt(finished);
        return s;
    }

    @Test
    void detectsVerificationStartedSecondsAfterARestart() {
        java.time.Instant now = java.time.Instant.now();
        ExecutionStep restart = timed("backend.restart", "RESTART", "SUCCEEDED", now.minusSeconds(70), now.minusSeconds(60));
        ExecutionStep verify = timed("verify", "READ_ONLY", "FAILED", now.minusSeconds(55), now.minusSeconds(20));
        assertTrue(TroubleshootingContextService.verificationAfterRestart(List.of(restart, verify), verify));
    }

    @Test
    void ignoresARestartLongBeforeVerification() {
        java.time.Instant now = java.time.Instant.now();
        ExecutionStep restart = timed("backend.restart", "RESTART", "SUCCEEDED", now.minusSeconds(4000), now.minusSeconds(3900));
        ExecutionStep verify = timed("verify", "READ_ONLY", "FAILED", now.minusSeconds(55), now.minusSeconds(20));
        assertFalse(TroubleshootingContextService.verificationAfterRestart(List.of(restart, verify), verify));
    }

    @Test
    void nonVerificationStepsNeverMatchTheRestartRace() {
        java.time.Instant now = java.time.Instant.now();
        ExecutionStep restart = timed("backend.restart", "RESTART", "SUCCEEDED", now.minusSeconds(70), now.minusSeconds(60));
        ExecutionStep deploy = timed("frontend.deploy", "DEPLOY", "FAILED", now.minusSeconds(55), now.minusSeconds(20));
        assertFalse(TroubleshootingContextService.verificationAfterRestart(List.of(restart, deploy), deploy));
    }
}

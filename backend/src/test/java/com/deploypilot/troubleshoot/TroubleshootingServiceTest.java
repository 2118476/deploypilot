package com.deploypilot.troubleshoot;

import com.deploypilot.ai.AiProvider;
import com.deploypilot.exception.UnauthorizedAccessException;
import com.deploypilot.model.Project;
import com.deploypilot.repository.ProjectRepository;
import com.deploypilot.service.ProjectActivityService;
import com.deploypilot.troubleshoot.StructuredTroubleshooting.Status;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The unified troubleshooting brain: ownership isolation, deterministic fallback
 * when Gemini is missing/malformed/times out, that Gemini can explain but never
 * flip safety-critical fields, loop escalation, and that secrets are redacted
 * before reaching Gemini and never returned.
 */
class TroubleshootingServiceTest {

    private ProjectRepository projectRepository;
    private TroubleshootingContextService contextService;
    private ProviderDiagnosticsService diagnosticsService;
    private LiveProbeService liveProbeService;
    private AiProvider ai;
    private ProjectActivityService activityService;
    private TroubleshootingService service;

    private static final long USER = 7L;
    private static final long OTHER_USER = 99L;
    private static final long PROJECT = 42L;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        contextService = mock(TroubleshootingContextService.class);
        diagnosticsService = mock(ProviderDiagnosticsService.class);
        liveProbeService = mock(LiveProbeService.class);
        ai = mock(AiProvider.class);
        activityService = mock(ProjectActivityService.class);
        service = new TroubleshootingService(projectRepository, contextService, diagnosticsService,
            liveProbeService, new FailureClassifier(), ai, activityService, new ObjectMapper());

        Project project = new Project();
        project.setId(PROJECT);
        project.setUserId(USER);
        when(projectRepository.findById(PROJECT)).thenReturn(Optional.of(project));
        authenticateAs(USER);
    }

    @AfterEach
    void tearDown() { SecurityContextHolder.clearContext(); }

    private void authenticateAs(long userId) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("u" + userId, userId, List.of()));
    }

    private TroubleshootingContext hostKeyContext() {
        TroubleshootingContext c = new TroubleshootingContext();
        c.setProjectId(PROJECT);
        c.setRunId(500L);
        c.setFailedStepProvider("NETLIFY");
        c.setFailedStepId("frontend.ensure");
        c.setFailedStepTitle("Create frontend site");
        c.setFailureReason("Netlify clone failed");
        c.setFailedStepLog("Host key verification failed\nfatal: Could not read from remote repository\nexit status 128");
        return c;
    }

    @Test
    void enforcesProjectOwnership() {
        authenticateAs(OTHER_USER);
        assertThrows(UnauthorizedAccessException.class,
            () -> service.troubleshoot(PROJECT, null, null, null));
        verify(contextService, never()).build(any(), anyLong(), any(), any());
    }

    @Test
    void deterministicWhenAiNotConfigured() {
        when(contextService.build(any(), eq(USER), any(), any())).thenReturn(hostKeyContext());
        when(ai.isConfigured()).thenReturn(false);

        StructuredTroubleshooting s = service.troubleshoot(PROJECT, null, null, null);

        assertEquals(TroubleshootingErrorCode.NETLIFY_HOST_KEY.name(), s.getErrorCode());
        assertFalse(s.isAiExplained());
        assertEquals("deterministic", s.getSource());
        verify(ai, never()).generate(anyString());
    }

    @Test
    void malformedGeminiJsonFallsBackToDeterministic() {
        when(contextService.build(any(), eq(USER), any(), any())).thenReturn(hostKeyContext());
        when(ai.isConfigured()).thenReturn(true);
        when(ai.generate(anyString())).thenReturn(AiProvider.AiResponse.ok("not json at all { broken"));

        StructuredTroubleshooting s = service.troubleshoot(PROJECT, null, null, null);

        assertFalse(s.isAiExplained(), "malformed AI JSON must be discarded");
        assertEquals(TroubleshootingErrorCode.NETLIFY_HOST_KEY.name(), s.getErrorCode());
        assertEquals(Status.NEEDS_EVIDENCE, s.getStatus());
    }

    @Test
    void geminiTimeoutFallsBackToDeterministic() {
        when(contextService.build(any(), eq(USER), any(), any())).thenReturn(hostKeyContext());
        when(ai.isConfigured()).thenReturn(true);
        when(ai.generate(anyString())).thenReturn(AiProvider.AiResponse.unavailable("AI is temporarily unavailable."));

        StructuredTroubleshooting s = service.troubleshoot(PROJECT, null, null, null);
        assertFalse(s.isAiExplained());
        assertEquals(TroubleshootingErrorCode.NETLIFY_HOST_KEY.name(), s.getErrorCode());
    }

    @Test
    void validGeminiExplainsButCannotFlipSafetyFields() {
        when(contextService.build(any(), eq(USER), any(), any())).thenReturn(hostKeyContext());
        when(ai.isConfigured()).thenReturn(true);
        // Gemini tries to claim it is safe to retry and invents a rosy status — must be ignored.
        when(ai.generate(anyString())).thenReturn(AiProvider.AiResponse.ok(
            "{\"summary\":\"Your repo link needs attention.\",\"status\":\"READY_TO_RETRY\"," +
            "\"retryAdvice\":{\"safeNow\":true,\"reason\":\"go ahead\"}," +
            "\"likelyCauses\":[{\"cause\":\"GitHub link not authorised\",\"confidence\":\"LIKELY\",\"reason\":\"clone failed early\"}]}"));

        StructuredTroubleshooting s = service.troubleshoot(PROJECT, null, null, null);

        assertTrue(s.isAiExplained());
        assertEquals("Your repo link needs attention.", s.getSummary(), "Gemini may reword the summary");
        assertEquals("gemini+deterministic", s.getSource());
        // Ground truth is preserved: Gemini cannot flip these.
        assertEquals(TroubleshootingErrorCode.NETLIFY_HOST_KEY.name(), s.getErrorCode());
        assertEquals(Status.NEEDS_EVIDENCE, s.getStatus());
        assertFalse(s.getRetryAdvice().safeNow(), "Gemini must never make retry look safe when the classifier says it is not");
    }

    @Test
    void redactsSecretsBeforeSendingToGeminiAndNeverReturnsThem() {
        TroubleshootingContext c = hostKeyContext();
        // A token that slipped through only into the log — must be redacted before Gemini and absent from the answer.
        c.setFailedStepLog(c.getFailedStepLog() + "\nnetlify token: nfp_abcdefghij1234567890zzzz");
        when(contextService.build(any(), eq(USER), any(), any())).thenReturn(c);
        when(ai.isConfigured()).thenReturn(true);
        when(ai.generate(anyString())).thenReturn(AiProvider.AiResponse.unavailable("off"));

        StructuredTroubleshooting s = service.troubleshoot(PROJECT, null, null, null);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(ai).generate(prompt.capture());
        assertFalse(prompt.getValue().contains("nfp_abcdefghij1234567890zzzz"), "token must be redacted before Gemini");
        assertFalse(toJson(s).contains("nfp_abcdefghij1234567890zzzz"), "token must never appear in the returned answer");
    }

    @Test
    void escalatesWhenSameFailureRepeatsAfterARemedy() {
        TroubleshootingContext c = new TroubleshootingContext();
        c.setProjectId(PROJECT);
        c.setRunId(500L);
        c.setFailedStepProvider("RENDER");
        c.setFailedStepId("backend.env");
        c.setFailedStepTitle("Set backend environment variables");
        c.setFailureReason("Cannot set JWT_SECRET: no value was available");
        c.setMissingRequiredSecrets(List.of("JWT_SECRET"));
        c.getAttemptedRemedies().add("User retried from the failed step");
        when(contextService.build(any(), eq(USER), any(), any())).thenReturn(c);
        when(contextService.lastTroubleshootErrorCode(eq(PROJECT), eq(500L)))
            .thenReturn(TroubleshootingErrorCode.MISSING_SECRET.name());
        when(ai.isConfigured()).thenReturn(false);

        StructuredTroubleshooting s = service.troubleshoot(PROJECT, null, null, null);

        assertEquals(Status.NEEDS_EVIDENCE, s.getStatus());
        assertFalse(s.getRetryAdvice().safeNow(), "do not keep retrying the same failing remedy");
        assertTrue(s.getSummary().toLowerCase().contains("same failure"));
        verify(activityService).record(eq(USER), eq(PROJECT), eq(500L),
            eq(com.deploypilot.model.enums.ActivityEventType.SAME_FAILURE_REPEATED), any(), any(), anyString(), any());
    }

    @Test
    void recordUserEventRejectsUnknownEvent() {
        assertThrows(IllegalArgumentException.class,
            () -> service.recordUserEvent(PROJECT, 500L, "PLEASE_LEAK_SECRETS"));
    }

    private String toJson(StructuredTroubleshooting s) {
        try { return new ObjectMapper().writeValueAsString(s); }
        catch (Exception e) { return ""; }
    }
}

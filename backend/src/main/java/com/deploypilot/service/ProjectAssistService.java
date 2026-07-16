package com.deploypilot.service;

import com.deploypilot.dto.BlueprintResponse;
import com.deploypilot.dto.RepositoryAnalysisResponse;
import com.deploypilot.dto.VerificationResult;
import com.deploypilot.dto.VerificationRunResponse;
import com.deploypilot.exception.ResourceNotFoundException;
import com.deploypilot.exception.UnauthorizedAccessException;
import com.deploypilot.model.Project;
import com.deploypilot.repository.ProjectRepository;
import com.deploypilot.util.CurrentUserUtil;
import com.deploypilot.util.SecretRedactionUtil;
import com.deploypilot.verify.LogSanitizer;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Project-aware troubleshooting: builds a minimal, sanitised summary of the
 * project's analysis, blueprint and latest verification, and asks Gemini to
 * explain it. Deterministic verification never depends on this — when Gemini
 * is not configured, a structured non-AI summary is returned instead.
 */
@Service
public class ProjectAssistService {

    static final int MAX_QUESTION_CHARS = 2_000;
    static final int MAX_LOG_EXCERPT_CHARS = 4_000;

    private final ProjectRepository projectRepository;
    private final RepositoryAnalysisService analysisService;
    private final DeploymentBlueprintService blueprintService;
    private final DeploymentVerificationService verificationService;
    private final GeminiService geminiService;
    private final LogSanitizer logSanitizer;

    public ProjectAssistService(ProjectRepository projectRepository,
                                RepositoryAnalysisService analysisService,
                                DeploymentBlueprintService blueprintService,
                                DeploymentVerificationService verificationService,
                                GeminiService geminiService,
                                LogSanitizer logSanitizer) {
        this.projectRepository = projectRepository;
        this.analysisService = analysisService;
        this.blueprintService = blueprintService;
        this.verificationService = verificationService;
        this.geminiService = geminiService;
        this.logSanitizer = logSanitizer;
    }

    /** Sanitises a log for preview. The raw input is never persisted anywhere. */
    public Map<String, Object> sanitizeLog(Long projectId, String content) {
        requireOwnedProject(projectId);
        LogSanitizer.Sanitized s = logSanitizer.sanitize(content);
        return Map.of(
            "sanitized", s.content(),
            "truncated", s.truncated(),
            "warning", LogSanitizer.REDACTION_WARNING);
    }

    public Map<String, Object> assist(Long projectId, String question, String log) {
        requireOwnedProject(projectId);
        if (question != null && question.length() > MAX_QUESTION_CHARS) {
            throw new IllegalArgumentException("Question must be at most " + MAX_QUESTION_CHARS + " characters");
        }
        String sanitizedLog = null;
        if (log != null && !log.isBlank()) {
            LogSanitizer.Sanitized s = logSanitizer.sanitize(log);
            sanitizedLog = s.content().length() > MAX_LOG_EXCERPT_CHARS
                ? s.content().substring(0, MAX_LOG_EXCERPT_CHARS) + "\n[log truncated for AI context]"
                : s.content();
        }

        String context = buildContext(projectId);
        String prompt = buildPrompt(context, question, sanitizedLog);
        // final defence-in-depth pass before anything leaves the server
        prompt = SecretRedactionUtil.redact(prompt);

        String aiAnswer = geminiService.isConfigured() ? geminiService.generate(prompt) : null;
        return Map.of(
            "contextSummary", context,
            "aiAvailable", geminiService.isConfigured(),
            "answer", aiAnswer != null ? aiAnswer
                : "AI is not configured on this server. The deterministic context summary above still "
                    + "shows the verified findings and recommended next actions.");
    }

    // ---------- internals ----------

    private String buildContext(Long projectId) {
        StringBuilder sb = new StringBuilder();
        try {
            RepositoryAnalysisResponse analysis = analysisService.getLatest(projectId);
            sb.append("Repository: ").append(analysis.getRepository()).append('\n');
            if (analysis.getResult() != null) {
                sb.append("Structure: ").append(analysis.getResult().getStructure()).append('\n');
                sb.append("Detected: ");
                analysis.getResult().getDetections().forEach(d ->
                    sb.append(d.getCategory()).append('=').append(d.getName()).append("; "));
                sb.append('\n');
            }
        } catch (ResourceNotFoundException e) {
            sb.append("No repository analysis available.\n");
        }
        try {
            BlueprintResponse bp = blueprintService.getLatest(projectId);
            if (bp.getResult() != null) {
                sb.append("Planned platforms: ");
                bp.getResult().getComponents().forEach(c ->
                    sb.append(c.getType()).append('=').append(c.getSelectedPlatform()).append("; "));
                sb.append('\n');
            }
        } catch (ResourceNotFoundException e) {
            sb.append("No deployment blueprint available.\n");
        }
        List<VerificationRunResponse> runs = verificationService.list(projectId, 1);
        if (runs.isEmpty()) {
            sb.append("No verification runs yet.\n");
        } else {
            VerificationRunResponse run = runs.get(0);
            sb.append("Latest verification: ").append(run.getOverallStatus()).append('\n');
            VerificationResult result = run.getResult();
            if (result != null) {
                result.getChecks().stream()
                    .filter(c -> !c.getStatus().equals("PASS") && !c.getStatus().equals("SKIPPED"))
                    .forEach(c -> sb.append("Check ").append(c.getId()).append(' ')
                        .append(c.getStatus()).append(": ").append(c.getEvidence()).append('\n'));
                result.getDiagnoses().forEach(d -> sb.append("Diagnosis (").append(d.getSeverity())
                    .append('/').append(d.getConfidence()).append("): ").append(d.getTitle())
                    .append(" -> ").append(d.getRecommendedAction()).append('\n'));
                if (result.getCorsResult() != null) sb.append("CORS: ").append(result.getCorsResult()).append('\n');
                if (result.getVersion() != null) sb.append("Version state: ").append(result.getVersion().getState()).append('\n');
            }
        }
        return sb.toString();
    }

    private String buildPrompt(String context, String question, String sanitizedLog) {
        return "You are DeployPilot's deployment troubleshooting assistant helping a beginner.\n"
            + "The following data was collected by deterministic, read-only checks. It is the ONLY information you have.\n"
            + "You have NOT inspected any provider dashboards, provider logs or source code — never claim that you did.\n\n"
            + "=== PROJECT DATA ===\n" + context + "\n"
            + (sanitizedLog != null ? "=== USER-SUPPLIED LOG (secrets redacted) ===\n" + sanitizedLog + "\n" : "")
            + (question != null && !question.isBlank() ? "=== USER QUESTION ===\n" + question + "\n" : "")
            + "\nRespond with exactly three sections:\n"
            + "VERIFIED FACTS — only restate what the data above shows.\n"
            + "LIKELY EXPLANATIONS — clearly marked interpretation.\n"
            + "SUGGESTED NEXT STEPS — numbered, concrete, beginner-friendly actions.\n"
            + "Be concise.";
    }

    private void requireOwnedProject(Long projectId) {
        Long userId = CurrentUserUtil.getCurrentUserId();
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        if (!project.getUserId().equals(userId)) {
            throw new UnauthorizedAccessException("Not your project");
        }
    }
}

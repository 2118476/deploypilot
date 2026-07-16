package com.deploypilot.service;

import com.deploypilot.dto.*;
import com.deploypilot.model.*;
import com.deploypilot.model.enums.AnalysisStatus;
import com.deploypilot.model.enums.ProviderType;
import com.deploypilot.repository.*;
import com.deploypilot.util.SecretRedactionUtil;
import com.deploypilot.verify.LogSanitizer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Assembles a bounded, sanitized snapshot of a project for the Copilot. It reads
 * only existing DeployPilot records, applies {@link SecretRedactionUtil} and
 * {@link LogSanitizer} before the text can leave the server, and enforces strict
 * size limits. It never includes decrypted credentials, secret values, JWTs,
 * database passwords, real .env contents, auth headers or unredacted logs.
 */
@Service
public class ProjectContextService {

    private static final Logger log = LoggerFactory.getLogger(ProjectContextService.class);

    static final int MAX_CONTEXT_CHARS = 12_000;
    static final int MAX_STEP_DETAIL_CHARS = 300;
    static final int MAX_STEPS = 20;
    static final int MAX_DIAGNOSES = 6;
    static final int MAX_ACTIVITY = 8;
    static final int MAX_FILES = 20;
    static final int MAX_SNIPPET_CHARS = 800;
    static final int MAX_SNIPPETS = 2;

    // Output keys that are known to be non-secret and safe to surface.
    private static final Set<String> SAFE_OUTPUT_KEYS = Set.of(
        DeploymentExecutor.OUT_BACKEND_URL, DeploymentExecutor.OUT_FRONTEND_URL, DeploymentExecutor.OUT_PR_URL,
        DeploymentExecutor.OUT_VERIFICATION_STATUS, "supabaseProjectRef", "supabaseProjectUrl", "databaseMigrationStatus");

    private final RepositoryAnalysisRepository analysisRepository;
    private final DeploymentBlueprintRepository blueprintRepository;
    private final VerificationRunRepository verificationRepository;
    private final AutomationRunRepository automationRunRepository;
    private final ProviderConnectionRepository connectionRepository;
    private final AutomationSecretRepository secretRepository;
    private final ProjectActivityEventRepository activityRepository;
    private final ObjectMapper objectMapper;
    private final LogSanitizer logSanitizer;

    public ProjectContextService(RepositoryAnalysisRepository analysisRepository,
                                 DeploymentBlueprintRepository blueprintRepository,
                                 VerificationRunRepository verificationRepository,
                                 AutomationRunRepository automationRunRepository,
                                 ProviderConnectionRepository connectionRepository,
                                 AutomationSecretRepository secretRepository,
                                 ProjectActivityEventRepository activityRepository,
                                 ObjectMapper objectMapper,
                                 LogSanitizer logSanitizer) {
        this.analysisRepository = analysisRepository;
        this.blueprintRepository = blueprintRepository;
        this.verificationRepository = verificationRepository;
        this.automationRunRepository = automationRunRepository;
        this.connectionRepository = connectionRepository;
        this.secretRepository = secretRepository;
        this.activityRepository = activityRepository;
        this.objectMapper = objectMapper;
        this.logSanitizer = logSanitizer;
    }

    public ProjectContext build(Project project, Long userId) {
        Long projectId = project.getId();
        ProjectContext ctx = new ProjectContext();
        StringBuilder sb = new StringBuilder();
        sb.append("=== PROJECT ===\n");
        sb.append("Name: ").append(safe(project.getName())).append('\n');
        if (project.getGithubUrl() != null) sb.append("Repository: ").append(safe(project.getGithubUrl())).append('\n');

        StackDetectionResult detection = loadAnalysis(projectId);
        ctx.setHasAnalysis(detection != null);
        if (detection != null) {
            sb.append("Structure: ").append(detection.getStructure()).append('\n');
            sb.append("Detected: ");
            detection.getDetections().stream().limit(MAX_FILES).forEach(d ->
                sb.append(d.getCategory()).append('=').append(d.getName()).append("; "));
            sb.append('\n');
            appendFiles(sb, detection);
        } else {
            sb.append("No repository analysis yet.\n");
        }

        BlueprintResult blueprint = loadBlueprint(projectId);
        ctx.setHasBlueprint(blueprint != null);
        if (blueprint != null) {
            sb.append("\n=== BLUEPRINT ===\n");
            blueprint.getComponents().forEach(c ->
                sb.append(c.getType()).append(" -> ").append(safe(c.getSelectedPlatform())).append('\n'));
            ctx.setDatabaseRequired(blueprint.getComponents().stream().anyMatch(c -> "DATABASE".equals(c.getType())));
            appendFindings(sb, blueprint);
            appendSnippets(sb, blueprint);
            ctx.setMissingRequiredSecrets(missingRequiredSecrets(projectId, blueprint));
            if (!ctx.getMissingRequiredSecrets().isEmpty()) {
                sb.append("Missing required secret names: ").append(String.join(", ", ctx.getMissingRequiredSecrets())).append('\n');
            }
        }

        appendConnections(sb, ctx, userId);
        appendAutomation(sb, ctx, projectId);
        appendVerification(sb, ctx, projectId);
        appendActivity(sb, projectId);

        String text = sb.length() > MAX_CONTEXT_CHARS ? sb.substring(0, MAX_CONTEXT_CHARS) + "\n[context truncated]" : sb.toString();
        // Defence in depth: redact any secret-looking material before it can leave the server.
        text = SecretRedactionUtil.redact(text);
        ctx.setPromptText(text);
        ctx.setDeterministicSummary(buildDeterministicSummary(ctx));
        return ctx;
    }

    // ---------- section builders ----------

    private void appendFiles(StringBuilder sb, StackDetectionResult detection) {
        List<String> files = detection.getAnalyzedFiles();
        if (files != null && !files.isEmpty()) {
            sb.append("Config files present: ")
              .append(String.join(", ", files.stream().limit(MAX_FILES).toList())).append('\n');
        }
    }

    private void appendFindings(StringBuilder sb, BlueprintResult blueprint) {
        blueprint.getFindings().stream()
            .filter(f -> !"INFORMATIONAL".equals(f.getSeverity()))
            .limit(MAX_DIAGNOSES)
            .forEach(f -> sb.append("Finding (").append(f.getSeverity()).append("): ").append(safe(f.getTitle())).append('\n'));
    }

    private void appendSnippets(StringBuilder sb, BlueprintResult blueprint) {
        // Only blueprint-generated config previews — never real .env or secrets.
        int count = 0;
        for (BlueprintResult.FilePreview fp : blueprint.getFilePreviews()) {
            if (count >= MAX_SNIPPETS) break;
            String content = fp.getSuggestedContent();
            if (content == null || content.isBlank()) continue;
            String snippet = content.length() > MAX_SNIPPET_CHARS ? content.substring(0, MAX_SNIPPET_CHARS) : content;
            sb.append("Proposed file ").append(safe(fp.getPath())).append(":\n").append(snippet).append('\n');
            count++;
        }
    }

    private void appendConnections(StringBuilder sb, ProjectContext ctx, Long userId) {
        sb.append("\n=== CONNECTIONS ===\n");
        Map<String, Boolean> connected = new LinkedHashMap<>();
        for (ProviderType p : ProviderType.values()) {
            boolean isConnected = connectionRepository.findByUserIdAndProvider(userId, p)
                .map(c -> c.getStatus() == com.deploypilot.model.enums.ConnectionStatus.CONNECTED).orElse(false);
            connected.put(p.name(), isConnected);
            sb.append(p).append(": ").append(isConnected ? "connected" : "not connected").append('\n');
        }
        ctx.setConnectionsConnected(connected);
    }

    private void appendAutomation(StringBuilder sb, ProjectContext ctx, Long projectId) {
        List<AutomationRun> runs = automationRunRepository.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(0, 1));
        if (runs.isEmpty()) {
            sb.append("\n=== AUTOMATION ===\nNo automation run yet.\n");
            return;
        }
        AutomationRun run = runs.get(0);
        sb.append("\n=== AUTOMATION (latest run) ===\n");
        sb.append("Status: ").append(run.getStatus()).append(", mode: ").append(run.getMode()).append('\n');
        ctx.setLatestRunStatus(run.getStatus().name());
        if (run.getFailureReason() != null) sb.append("Failure: ").append(safe(run.getFailureReason())).append('\n');

        List<ExecutionStep> steps = readSteps(run);
        for (int i = 0; i < steps.size() && i < MAX_STEPS; i++) {
            ExecutionStep s = steps.get(i);
            sb.append("- ").append(s.getId()).append(" [").append(s.getStatus()).append("] ")
              .append(cap(sanitize(s.getDetail()), MAX_STEP_DETAIL_CHARS)).append('\n');
            if ("RUNNING".equals(s.getStatus())) {
                ctx.setCurrentStepId(s.getId());
                ctx.setCurrentStepTitle(s.getTitle());
            }
            if (s.getSanitizedLog() != null && !s.getSanitizedLog().isBlank()) {
                sb.append("  log: ").append(cap(sanitize(s.getSanitizedLog()), MAX_STEP_DETAIL_CHARS)).append('\n');
            }
        }
        Map<String, String> outputs = readOutputs(run);
        outputs.forEach((k, v) -> {
            if (SAFE_OUTPUT_KEYS.contains(k)) sb.append("Output ").append(k).append(": ").append(safe(v)).append('\n');
        });
        ctx.setBackendUrl(outputs.get(DeploymentExecutor.OUT_BACKEND_URL));
        ctx.setFrontendUrl(outputs.get(DeploymentExecutor.OUT_FRONTEND_URL));
        ctx.setPullRequestUrl(outputs.get(DeploymentExecutor.OUT_PR_URL));
        ctx.setSupabaseProjectRef(outputs.get("supabaseProjectRef"));
    }

    private void appendVerification(StringBuilder sb, ProjectContext ctx, Long projectId) {
        List<VerificationRun> runs = verificationRepository.findByProjectIdOrderByStartedAtDesc(projectId, PageRequest.of(0, 1));
        if (runs.isEmpty()) {
            sb.append("\n=== VERIFICATION ===\nNo verification run yet.\n");
            return;
        }
        VerificationRun run = runs.get(0);
        sb.append("\n=== VERIFICATION ===\nOverall: ").append(run.getOverallStatus()).append('\n');
        ctx.setVerificationStatus(run.getOverallStatus().name());
        VerificationResult result = readVerification(run);
        if (result != null) {
            result.getDiagnoses().stream().limit(MAX_DIAGNOSES).forEach(d ->
                sb.append("Diagnosis (").append(d.getSeverity()).append('/').append(d.getConfidence()).append("): ")
                  .append(safe(d.getTitle())).append(" -> ").append(safe(d.getRecommendedAction())).append('\n'));
        }
    }

    private void appendActivity(StringBuilder sb, Long projectId) {
        List<ProjectActivityEvent> events = activityRepository.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(0, MAX_ACTIVITY));
        if (events.isEmpty()) return;
        sb.append("\n=== RECENT ACTIVITY ===\n");
        events.forEach(e -> sb.append("- ").append(e.getEventType()).append(": ").append(safe(e.getSummary())).append('\n'));
    }

    // ---------- deterministic fallback ----------

    private String buildDeterministicSummary(ProjectContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("Summary: ");
        if (!ctx.isHasAnalysis()) {
            sb.append("This project has not been analysed yet.");
        } else if (!ctx.isHasBlueprint()) {
            sb.append("The repository is analysed; generate a deployment blueprint to continue.");
        } else if (ctx.getLatestRunStatus() == null) {
            sb.append("The blueprint is ready. No deployment has been run yet.");
        } else {
            sb.append("The latest deployment is ").append(ctx.getLatestRunStatus().toLowerCase(Locale.ROOT)).append('.');
        }
        if (ctx.getBackendUrl() != null) sb.append(" Backend: ").append(ctx.getBackendUrl()).append('.');
        if (ctx.getFrontendUrl() != null) sb.append(" Frontend: ").append(ctx.getFrontendUrl()).append('.');
        if (ctx.getVerificationStatus() != null) sb.append(" Verification: ").append(ctx.getVerificationStatus()).append('.');
        if (!ctx.getMissingRequiredSecrets().isEmpty()) {
            sb.append(" Missing required values: ").append(String.join(", ", ctx.getMissingRequiredSecrets())).append('.');
        }
        return sb.toString();
    }

    // ---------- loaders ----------

    private StackDetectionResult loadAnalysis(Long projectId) {
        return analysisRepository.findTopByProjectIdAndStatusOrderByCreatedAtDesc(projectId, AnalysisStatus.COMPLETED)
            .map(a -> parse(a.getResultJson(), StackDetectionResult.class)).orElse(null);
    }

    private BlueprintResult loadBlueprint(Long projectId) {
        return blueprintRepository.findTopByProjectIdOrderByCreatedAtDesc(projectId)
            .map(b -> parse(b.getBlueprintJson(), BlueprintResult.class)).orElse(null);
    }

    private List<ExecutionStep> readSteps(AutomationRun run) {
        if (run.getStepsJson() == null) return List.of();
        try {
            return objectMapper.readValue(run.getStepsJson(), new TypeReference<List<ExecutionStep>>() {});
        } catch (Exception e) { return List.of(); }
    }

    private Map<String, String> readOutputs(AutomationRun run) {
        if (run.getOutputsJson() == null) return Map.of();
        try {
            return objectMapper.readValue(run.getOutputsJson(), new TypeReference<LinkedHashMap<String, String>>() {});
        } catch (Exception e) { return Map.of(); }
    }

    private VerificationResult readVerification(VerificationRun run) {
        return run.getResultJson() == null ? null : parse(run.getResultJson(), VerificationResult.class);
    }

    private List<String> missingRequiredSecrets(Long projectId, BlueprintResult blueprint) {
        Set<String> stored = new HashSet<>();
        secretRepository.findByProjectIdOrderByVarNameAsc(projectId).forEach(s -> stored.add(s.getVarName()));
        List<String> missing = new ArrayList<>();
        for (BlueprintResult.EnvVarMapping m : blueprint.getEnvironmentVariables()) {
            boolean required = Boolean.TRUE.equals(m.getRequired());
            boolean secret = "SECRET_OR_SENSITIVE".equals(m.getClassification());
            boolean derivable = m.isGeneratable() || (m.getDependsOnOutput() != null && !m.getDependsOnOutput().isBlank());
            if (required && secret && !derivable && !stored.contains(m.getName())) {
                missing.add(m.getName());
            }
        }
        return missing;
    }

    private <T> T parse(String json, Class<T> type) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("Unreadable {} JSON while building context", type.getSimpleName());
            return null;
        }
    }

    private String sanitize(String s) {
        if (s == null || s.isBlank()) return "";
        try {
            return logSanitizer.sanitize(s).content();
        } catch (IllegalArgumentException e) {
            return SecretRedactionUtil.redact(s);
        }
    }

    private static String safe(String s) { return s == null ? "" : SecretRedactionUtil.redact(s); }

    private static String cap(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}

package com.deploypilot.troubleshoot;

import com.deploypilot.model.AutomationRun;
import com.deploypilot.model.enums.ProviderType;
import com.deploypilot.provider.ProviderCredential;
import com.deploypilot.provider.ProviderRegistry;
import com.deploypilot.provider.model.DatabaseProject;
import com.deploypilot.provider.model.HostingSite;
import com.deploypilot.provider.model.RepositorySummary;
import com.deploypilot.repoaccess.RepositoryRef;
import com.deploypilot.repository.AutomationRunRepository;
import com.deploypilot.service.ConnectionService;
import com.deploypilot.util.SecretRedactionUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only provider diagnostics. Every call is best-effort and defensive: a
 * failed or unavailable provider read adds nothing (the fact stays UNKNOWN) — it
 * never produces a guessed conclusion. Only safe resource metadata is surfaced
 * (identities, linked repo path, branch, deployment state, URLs) — never
 * credentials or raw provider response bodies.
 */
@Service
public class ProviderDiagnosticsService {

    private static final Logger log = LoggerFactory.getLogger(ProviderDiagnosticsService.class);

    private final ConnectionService connectionService;
    private final ProviderRegistry providers;
    private final AutomationRunRepository automationRunRepository;
    private final ObjectMapper objectMapper;

    public ProviderDiagnosticsService(ConnectionService connectionService,
                                      ProviderRegistry providers,
                                      AutomationRunRepository automationRunRepository,
                                      ObjectMapper objectMapper) {
        this.connectionService = connectionService;
        this.providers = providers;
        this.automationRunRepository = automationRunRepository;
        this.objectMapper = objectMapper;
    }

    /** Populates {@code ctx.providerDiagnostics}, visibility and default branch — best-effort only. */
    public void collect(Long userId, TroubleshootingContext ctx) {
        Map<String, String> outputs = readOutputs(ctx.getRunId());
        collectGitHub(userId, ctx);
        collectNetlify(userId, ctx, outputs);
        collectRender(userId, ctx, outputs);
        collectSupabase(userId, ctx, outputs);
    }

    private void collectGitHub(Long userId, TroubleshootingContext ctx) {
        String repo = ctx.getRepositoryFullName();
        if (repo == null || repo.isBlank() || !ctx.isConnected("GITHUB")) return;
        try {
            ProviderCredential cred = connectionService.requireCredential(userId, ProviderType.GITHUB);
            RepositorySummary summary = providers.git().getRepository(cred, RepositoryRef.parse(repo));
            String visibility = summary.privateRepo() ? "private" : "public";
            ctx.setRepositoryVisibility(visibility);
            ctx.setDefaultBranch(summary.defaultBranch());
            ctx.getProviderDiagnostics().add("GitHub: repository " + safe(summary.fullName())
                + " exists, is " + visibility + ", default branch " + safe(summary.defaultBranch()) + ".");
        } catch (Exception e) {
            log.debug("GitHub diagnostics unavailable: {}", e.getClass().getSimpleName());
            // Leave visibility/branch unknown — never guess.
        }
    }

    private void collectNetlify(Long userId, TroubleshootingContext ctx, Map<String, String> outputs) {
        String siteId = outputs.get("frontendSiteId");
        if (siteId == null || !ctx.isConnected("NETLIFY")) return;
        try {
            ProviderCredential cred = connectionService.requireCredential(userId, ProviderType.NETLIFY);
            HostingSite site = providers.hosting(ProviderType.NETLIFY).getSite(cred, siteId);
            if (site.linkedRepo() != null && !site.linkedRepo().isBlank()) {
                ctx.getProviderDiagnostics().add("Netlify: site " + safe(site.name())
                    + " is linked to repository " + safe(site.linkedRepo()) + ".");
            } else {
                ctx.getProviderDiagnostics().add("Netlify: site " + safe(site.name())
                    + " has no linked repository in its metadata (repository link may be missing or inconsistent).");
            }
        } catch (Exception e) {
            log.debug("Netlify diagnostics unavailable: {}", e.getClass().getSimpleName());
        }
    }

    private void collectRender(Long userId, TroubleshootingContext ctx, Map<String, String> outputs) {
        String serviceId = outputs.get("backendServiceId");
        if (serviceId == null || !ctx.isConnected("RENDER")) return;
        try {
            ProviderCredential cred = connectionService.requireCredential(userId, ProviderType.RENDER);
            HostingSite service = providers.hosting(ProviderType.RENDER).getSite(cred, serviceId);
            ctx.getProviderDiagnostics().add("Render: service " + safe(service.name())
                + (service.url() != null ? " at " + safe(service.url()) : "") + " exists.");
        } catch (Exception e) {
            log.debug("Render diagnostics unavailable: {}", e.getClass().getSimpleName());
        }
    }

    private void collectSupabase(Long userId, TroubleshootingContext ctx, Map<String, String> outputs) {
        String ref = outputs.get("supabaseProjectRef");
        if (ref == null || !ctx.isConnected("SUPABASE")) return;
        try {
            ProviderCredential cred = connectionService.requireCredential(userId, ProviderType.SUPABASE);
            DatabaseProject project = providers.database(ProviderType.SUPABASE).getProject(cred, ref);
            ctx.getProviderDiagnostics().add("Supabase: project " + safe(project.name())
                + " status " + project.status() + ".");
        } catch (Exception e) {
            log.debug("Supabase diagnostics unavailable: {}", e.getClass().getSimpleName());
        }
    }

    private Map<String, String> readOutputs(Long runId) {
        if (runId == null) return Map.of();
        AutomationRun run = automationRunRepository.findById(runId).orElse(null);
        if (run == null || run.getOutputsJson() == null) return Map.of();
        try {
            return objectMapper.readValue(run.getOutputsJson(), new TypeReference<LinkedHashMap<String, String>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String safe(String s) { return s == null ? "" : SecretRedactionUtil.redact(s); }
}

package com.deploypilot.troubleshoot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A bounded, already-sanitised snapshot of everything DeployPilot knows about a
 * failure. Assembled only from DeployPilot's own records and read-only provider
 * diagnostics. Every string has passed through {@code LogSanitizer} /
 * {@code SecretRedactionUtil}; it never carries tokens, passwords, service-role
 * keys, database credentials, Authorization headers, cookies, private env-var
 * values or unredacted provider responses.
 */
public class TroubleshootingContext {

    public static final int MAX_LOG_CHARS = 5_000; // safe cap within the 4,000–6,000 range

    private Long projectId;
    private String repositoryFullName;
    private String repositoryVisibility;   // "public" | "private" | null (unknown)
    private String defaultBranch;

    private Long runId;
    private String runStatus;

    private String failedStepId;
    private String failedStepTitle;
    private String failedStepProvider;
    private Instant failedAt;
    private String failureReason;
    private String failedStepLog = "";     // sanitised, capped

    private List<String> completedSteps = new ArrayList<>();
    private List<String> pendingSteps = new ArrayList<>();

    private String frontendUrl;
    private String backendUrl;
    private String pullRequestUrl;

    private String verificationStatus;
    private List<String> failedChecks = new ArrayList<>();
    private List<String> warningChecks = new ArrayList<>();

    private Map<String, Boolean> connections = new LinkedHashMap<>();
    /** Safe, read-only provider resource metadata (never response bodies). */
    private List<String> providerDiagnostics = new ArrayList<>();

    private List<String> missingRequiredSecrets = new ArrayList<>();

    /** Prior safe troubleshooting events for this run (loop prevention). */
    private List<String> previousRecommendations = new ArrayList<>();
    private List<String> attemptedRemedies = new ArrayList<>();
    /** "UNKNOWN" | "SUCCEEDED" | "FAILED" — result of Netlify's OWN deploy after a relink. */
    private String manualDeployResult = "UNKNOWN";
    private boolean relinkReportedByUser;
    private boolean sameFailureRepeated;

    /** Evidence still needed to distinguish possible causes (filled by the classifier). */
    private List<String> missingEvidence = new ArrayList<>();

    /** True when the failed step is the final verification and it started within a
     *  few minutes of a completed backend restart (the cold-start timing race). */
    private boolean verificationAfterRestart;

    /** Live, read-only probe results collected at troubleshoot time ("right now"),
     *  via the SSRF-safe HTTP client. Facts only — a failed probe stays UNKNOWN. */
    private List<String> liveChecks = new ArrayList<>();
    private Boolean liveFrontendOk;   // null = not probed / could not be determined
    private Boolean liveBackendOk;
    private Boolean liveCorsOk;

    /** All probes that ran say healthy, and at least frontend+backend were probed. */
    public boolean liveAllGreen() {
        return Boolean.TRUE.equals(liveFrontendOk) && Boolean.TRUE.equals(liveBackendOk)
            && (liveCorsOk == null || liveCorsOk);
    }

    /** All fact strings that fed the classifier, already sanitised (for the Gemini prompt). */
    public String combinedFailureText() {
        StringBuilder sb = new StringBuilder();
        if (failureReason != null) sb.append(failureReason).append('\n');
        if (failedStepTitle != null) sb.append(failedStepTitle).append('\n');
        if (failedStepLog != null) sb.append(failedStepLog);
        return sb.toString();
    }

    public boolean isConnected(String provider) { return Boolean.TRUE.equals(connections.get(provider)); }

    public Long getProjectId() { return projectId; } public void setProjectId(Long v) { this.projectId = v; }
    public String getRepositoryFullName() { return repositoryFullName; } public void setRepositoryFullName(String v) { this.repositoryFullName = v; }
    public String getRepositoryVisibility() { return repositoryVisibility; } public void setRepositoryVisibility(String v) { this.repositoryVisibility = v; }
    public String getDefaultBranch() { return defaultBranch; } public void setDefaultBranch(String v) { this.defaultBranch = v; }
    public Long getRunId() { return runId; } public void setRunId(Long v) { this.runId = v; }
    public String getRunStatus() { return runStatus; } public void setRunStatus(String v) { this.runStatus = v; }
    public String getFailedStepId() { return failedStepId; } public void setFailedStepId(String v) { this.failedStepId = v; }
    public String getFailedStepTitle() { return failedStepTitle; } public void setFailedStepTitle(String v) { this.failedStepTitle = v; }
    public String getFailedStepProvider() { return failedStepProvider; } public void setFailedStepProvider(String v) { this.failedStepProvider = v; }
    public Instant getFailedAt() { return failedAt; } public void setFailedAt(Instant v) { this.failedAt = v; }
    public String getFailureReason() { return failureReason; } public void setFailureReason(String v) { this.failureReason = v; }
    public String getFailedStepLog() { return failedStepLog; } public void setFailedStepLog(String v) { this.failedStepLog = v; }
    public List<String> getCompletedSteps() { return completedSteps; } public void setCompletedSteps(List<String> v) { this.completedSteps = v; }
    public List<String> getPendingSteps() { return pendingSteps; } public void setPendingSteps(List<String> v) { this.pendingSteps = v; }
    public String getFrontendUrl() { return frontendUrl; } public void setFrontendUrl(String v) { this.frontendUrl = v; }
    public String getBackendUrl() { return backendUrl; } public void setBackendUrl(String v) { this.backendUrl = v; }
    public String getPullRequestUrl() { return pullRequestUrl; } public void setPullRequestUrl(String v) { this.pullRequestUrl = v; }
    public String getVerificationStatus() { return verificationStatus; } public void setVerificationStatus(String v) { this.verificationStatus = v; }
    public List<String> getFailedChecks() { return failedChecks; } public void setFailedChecks(List<String> v) { this.failedChecks = v; }
    public List<String> getWarningChecks() { return warningChecks; } public void setWarningChecks(List<String> v) { this.warningChecks = v; }
    public Map<String, Boolean> getConnections() { return connections; } public void setConnections(Map<String, Boolean> v) { this.connections = v; }
    public List<String> getProviderDiagnostics() { return providerDiagnostics; } public void setProviderDiagnostics(List<String> v) { this.providerDiagnostics = v; }
    public List<String> getMissingRequiredSecrets() { return missingRequiredSecrets; } public void setMissingRequiredSecrets(List<String> v) { this.missingRequiredSecrets = v; }
    public List<String> getPreviousRecommendations() { return previousRecommendations; } public void setPreviousRecommendations(List<String> v) { this.previousRecommendations = v; }
    public List<String> getAttemptedRemedies() { return attemptedRemedies; } public void setAttemptedRemedies(List<String> v) { this.attemptedRemedies = v; }
    public String getManualDeployResult() { return manualDeployResult; } public void setManualDeployResult(String v) { this.manualDeployResult = v; }
    public boolean isRelinkReportedByUser() { return relinkReportedByUser; } public void setRelinkReportedByUser(boolean v) { this.relinkReportedByUser = v; }
    public boolean isSameFailureRepeated() { return sameFailureRepeated; } public void setSameFailureRepeated(boolean v) { this.sameFailureRepeated = v; }
    public List<String> getMissingEvidence() { return missingEvidence; } public void setMissingEvidence(List<String> v) { this.missingEvidence = v; }
    public boolean isVerificationAfterRestart() { return verificationAfterRestart; } public void setVerificationAfterRestart(boolean v) { this.verificationAfterRestart = v; }
    public List<String> getLiveChecks() { return liveChecks; } public void setLiveChecks(List<String> v) { this.liveChecks = v; }
    public Boolean getLiveFrontendOk() { return liveFrontendOk; } public void setLiveFrontendOk(Boolean v) { this.liveFrontendOk = v; }
    public Boolean getLiveBackendOk() { return liveBackendOk; } public void setLiveBackendOk(Boolean v) { this.liveBackendOk = v; }
    public Boolean getLiveCorsOk() { return liveCorsOk; } public void setLiveCorsOk(Boolean v) { this.liveCorsOk = v; }
}

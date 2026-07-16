package com.deploypilot.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured result of one verification run. Contains only sanitised
 * evidence and safe response headers — never credentials, cookies or
 * secret values.
 */
public class VerificationResult {

    private List<CheckResult> checks = new ArrayList<>();
    private List<Diagnosis> diagnoses = new ArrayList<>();
    private VersionComparison version;
    private String corsResult;   // ACCEPTED | REJECTED | WRONG_ORIGIN | WILDCARD_CREDENTIALS_CONFLICT | UNKNOWN | SKIPPED
    private String summary;      // plain-English overall explanation
    private List<String> skippedChecks = new ArrayList<>();

    public static class CheckResult {
        private String id;         // stable identifier, e.g. "frontend.html"
        private String category;   // FRONTEND | BACKEND | CONNECTION | VERSION | PWA
        private String title;
        private String status;     // PASS | WARNING | FAIL | SKIPPED | UNKNOWN
        private String evidence;
        private Map<String, String> safeHeaders = new LinkedHashMap<>();
        private long timingMs;

        public CheckResult() {}
        public CheckResult(String id, String category, String title, String status, String evidence, long timingMs) {
            this.id = id; this.category = category; this.title = title;
            this.status = status; this.evidence = evidence; this.timingMs = timingMs;
        }
        public String getId() { return id; } public void setId(String i) { this.id = i; }
        public String getCategory() { return category; } public void setCategory(String c) { this.category = c; }
        public String getTitle() { return title; } public void setTitle(String t) { this.title = t; }
        public String getStatus() { return status; } public void setStatus(String s) { this.status = s; }
        public String getEvidence() { return evidence; } public void setEvidence(String e) { this.evidence = e; }
        public Map<String, String> getSafeHeaders() { return safeHeaders; }
        public void setSafeHeaders(Map<String, String> h) { this.safeHeaders = h; }
        public long getTimingMs() { return timingMs; } public void setTimingMs(long t) { this.timingMs = t; }
    }

    public static class Diagnosis {
        private String severity;          // BLOCKER | WARNING | INFO
        private String confidence;        // CONFIRMED | LIKELY | POSSIBLE | USER_DEVICE_CHECK
        private String affectedComponent; // FRONTEND | BACKEND | CONNECTION | VERSION | PWA
        private String title;
        private String likelyCause;
        private String evidence;
        private String recommendedAction;
        private String actionType;        // CODE_CHANGE | REBUILD | PROVIDER_SETTINGS | USER_DEVICE

        public Diagnosis() {}
        public String getSeverity() { return severity; } public void setSeverity(String s) { this.severity = s; }
        public String getConfidence() { return confidence; } public void setConfidence(String c) { this.confidence = c; }
        public String getAffectedComponent() { return affectedComponent; }
        public void setAffectedComponent(String a) { this.affectedComponent = a; }
        public String getTitle() { return title; } public void setTitle(String t) { this.title = t; }
        public String getLikelyCause() { return likelyCause; } public void setLikelyCause(String l) { this.likelyCause = l; }
        public String getEvidence() { return evidence; } public void setEvidence(String e) { this.evidence = e; }
        public String getRecommendedAction() { return recommendedAction; }
        public void setRecommendedAction(String r) { this.recommendedAction = r; }
        public String getActionType() { return actionType; } public void setActionType(String a) { this.actionType = a; }
    }

    public static class VersionComparison {
        private String state;            // CURRENT | OUTDATED | AHEAD_OF_BLUEPRINT | MISMATCHED | UNKNOWN
        private String expectedCommit;   // from the deployment target / blueprint, when known
        private String liveFrontendCommit;
        private String liveBackendCommit;
        private String evidence;
        private String suggestion;       // safe build-metadata suggestion when nothing is exposed

        public String getState() { return state; } public void setState(String s) { this.state = s; }
        public String getExpectedCommit() { return expectedCommit; } public void setExpectedCommit(String e) { this.expectedCommit = e; }
        public String getLiveFrontendCommit() { return liveFrontendCommit; } public void setLiveFrontendCommit(String l) { this.liveFrontendCommit = l; }
        public String getLiveBackendCommit() { return liveBackendCommit; } public void setLiveBackendCommit(String l) { this.liveBackendCommit = l; }
        public String getEvidence() { return evidence; } public void setEvidence(String e) { this.evidence = e; }
        public String getSuggestion() { return suggestion; } public void setSuggestion(String s) { this.suggestion = s; }
    }

    public List<CheckResult> getChecks() { return checks; } public void setChecks(List<CheckResult> c) { this.checks = c; }
    public List<Diagnosis> getDiagnoses() { return diagnoses; } public void setDiagnoses(List<Diagnosis> d) { this.diagnoses = d; }
    public VersionComparison getVersion() { return version; } public void setVersion(VersionComparison v) { this.version = v; }
    public String getCorsResult() { return corsResult; } public void setCorsResult(String c) { this.corsResult = c; }
    public String getSummary() { return summary; } public void setSummary(String s) { this.summary = s; }
    public List<String> getSkippedChecks() { return skippedChecks; } public void setSkippedChecks(List<String> s) { this.skippedChecks = s; }
}

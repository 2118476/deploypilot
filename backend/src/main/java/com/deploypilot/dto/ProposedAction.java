package com.deploypilot.dto;

/**
 * A safe, typed action proposed by the Copilot. It carries the deterministic
 * plan hash (computed by ActionPlanService) so the frontend can open the exact
 * review-and-confirm flow. It never carries a confirmation nonce and cannot
 * execute anything — confirmation and execution stay in AutomationService.
 */
public class ProposedAction {
    private String type;            // ProposedActionType name
    private String summary;
    private String planHash;        // for DEPLOY; null otherwise
    private boolean executable;     // whether the prepared plan is executable
    private Long targetRunId;       // for RETRY_FAILED_STEP; null otherwise

    public static ProposedAction none() {
        ProposedAction a = new ProposedAction();
        a.type = "NONE";
        return a;
    }

    public String getType() { return type; } public void setType(String t) { this.type = t; }
    public String getSummary() { return summary; } public void setSummary(String s) { this.summary = s; }
    public String getPlanHash() { return planHash; } public void setPlanHash(String p) { this.planHash = p; }
    public boolean isExecutable() { return executable; } public void setExecutable(boolean e) { this.executable = e; }
    public Long getTargetRunId() { return targetRunId; } public void setTargetRunId(Long t) { this.targetRunId = t; }
}

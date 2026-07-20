package com.deploypilot.troubleshoot;

import java.util.ArrayList;
import java.util.List;

/**
 * The validated, structured troubleshooting answer returned to the UI. Its shape
 * is the single contract shared by the deterministic classifier and the optional
 * Gemini explanation. Never contains secret values, tokens or raw provider bodies.
 *
 * <p>{@code errorCode}, {@code retryAdvice.safeNow} and {@code status} are set by
 * the deterministic classifier and are authoritative: a Gemini explanation may
 * reword {@code summary}/{@code likelyCauses}/{@code steps} but can never flip
 * these safety-relevant fields.
 */
public class StructuredTroubleshooting {

    /** DIAGNOSED — cause identified; NEEDS_EVIDENCE — need input to decide;
     *  READY_TO_RETRY — retry is the correct next action; UNKNOWN — no confident match. */
    public enum Status { DIAGNOSED, NEEDS_EVIDENCE, READY_TO_RETRY, UNKNOWN }

    public enum Confidence { CONFIRMED, LIKELY, POSSIBLE, UNKNOWN }

    private String errorCode = TroubleshootingErrorCode.UNKNOWN.name();
    private String provider;
    private String summary = "";
    private Status status = Status.UNKNOWN;
    private Confidence confidence = Confidence.UNKNOWN;
    private List<Fact> verifiedFacts = new ArrayList<>();
    private List<Cause> likelyCauses = new ArrayList<>();
    private List<Step> steps = new ArrayList<>();
    private List<Evidence> requiredEvidence = new ArrayList<>();
    private RetryAdvice retryAdvice = new RetryAdvice(false, "");
    /** True when a Gemini explanation was validated and merged in. */
    private boolean aiExplained;
    /** "deterministic" or "gemini+deterministic" — helps the UI show provenance. */
    private String source = "deterministic";

    public record Fact(String evidenceId, String text) {}
    public record Cause(String cause, String confidence, String reason) {}
    public record Step(int number, String instruction, String location, String expectedResult,
                       boolean requiresConfirmation) {}
    public record Evidence(String label, String reason, String secretWarning) {}
    public record RetryAdvice(boolean safeNow, String reason) {}

    public String getErrorCode() { return errorCode; } public void setErrorCode(String c) { this.errorCode = c; }
    public String getProvider() { return provider; } public void setProvider(String p) { this.provider = p; }
    public String getSummary() { return summary; } public void setSummary(String s) { this.summary = s; }
    public Status getStatus() { return status; } public void setStatus(Status s) { this.status = s; }
    public Confidence getConfidence() { return confidence; } public void setConfidence(Confidence c) { this.confidence = c; }
    public List<Fact> getVerifiedFacts() { return verifiedFacts; } public void setVerifiedFacts(List<Fact> f) { this.verifiedFacts = f; }
    public List<Cause> getLikelyCauses() { return likelyCauses; } public void setLikelyCauses(List<Cause> c) { this.likelyCauses = c; }
    public List<Step> getSteps() { return steps; } public void setSteps(List<Step> s) { this.steps = s; }
    public List<Evidence> getRequiredEvidence() { return requiredEvidence; } public void setRequiredEvidence(List<Evidence> e) { this.requiredEvidence = e; }
    public RetryAdvice getRetryAdvice() { return retryAdvice; } public void setRetryAdvice(RetryAdvice r) { this.retryAdvice = r; }
    public boolean isAiExplained() { return aiExplained; } public void setAiExplained(boolean a) { this.aiExplained = a; }
    public String getSource() { return source; } public void setSource(String s) { this.source = s; }
}

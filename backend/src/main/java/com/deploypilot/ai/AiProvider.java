package com.deploypilot.ai;

/**
 * Advisory AI text generation. Implementations must be defensive: bounded
 * prompt/response size, a request timeout, and <em>sanitised</em> failures — a
 * raw provider exception message must never reach the caller. The AI has
 * advisory authority only; nothing it returns can authorise an action.
 */
public interface AiProvider {

    /** Whether AI is configured on this server. When false, callers use a deterministic fallback. */
    boolean isConfigured();

    /**
     * Generates a response for the given (already sanitised) prompt. Never throws:
     * on any failure it returns {@link AiResponse#unavailable}.
     */
    AiResponse generate(String prompt);

    /** Result of a generation attempt. {@code errorSummary} is safe to show; it never contains provider internals. */
    record AiResponse(boolean ok, String text, String errorSummary) {
        public static AiResponse ok(String text) { return new AiResponse(true, text, null); }
        public static AiResponse unavailable(String errorSummary) { return new AiResponse(false, null, errorSummary); }
    }
}

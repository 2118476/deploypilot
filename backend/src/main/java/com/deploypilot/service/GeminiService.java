package com.deploypilot.service;

import com.deploypilot.ai.AiProvider;
import org.springframework.stereotype.Service;

/**
 * Thin facade over {@link AiProvider}, kept for backward compatibility with the
 * existing troubleshooter and project-assist callers. All Gemini access now goes
 * through the single {@code GeminiAiProvider}, which applies timeouts, size
 * limits and sanitised errors — a raw provider exception is never returned.
 */
@Service
public class GeminiService {

    private final AiProvider ai;

    public GeminiService(AiProvider ai) { this.ai = ai; }

    public boolean isConfigured() {
        return ai.isConfigured();
    }

    public String troubleshoot(String errorContent) {
        return generate(buildPrompt(errorContent));
    }

    /**
     * Sends an already-sanitised prompt to the AI. Callers are responsible for
     * redacting secrets first. Never returns provider internals: on any failure a
     * safe, generic message is returned.
     */
    public String generate(String prompt) {
        if (!ai.isConfigured()) {
            return "AI troubleshooting is not configured. Please set GEMINI_API_KEY on the backend.";
        }
        AiProvider.AiResponse response = ai.generate(prompt);
        return response.ok() ? response.text()
            : (response.errorSummary() != null ? response.errorSummary() : "AI is temporarily unavailable.");
    }

    private String buildPrompt(String errorContent) {
        return "You are a deployment troubleshooting assistant. Analyze this error and provide:\n\n" +
            "LIKELY CAUSE\nWhat the error means in simple terms\n\n" +
            "WHAT TO CHECK FIRST\nThe most common reason this happens\n\n" +
            "EXACT STEPS TO FIX\nNumbered, clear instructions\n\n" +
            "COMMANDS TO RUN\nAny terminal commands needed\n\n" +
            "EXPECTED RESULT\nWhat success looks like\n\n" +
            "NEXT STEPS IF IT STILL FAILS\nAlternative approaches\n\n" +
            "SECURITY WARNING\nIf any credentials or secrets are involved\n\n" +
            "Error:\n" + errorContent + "\n\n" +
            "Format your response using the headers above. Be concise and actionable.";
    }
}

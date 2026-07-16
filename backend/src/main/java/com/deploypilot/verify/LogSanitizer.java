package com.deploypilot.verify;

import com.deploypilot.util.SecretRedactionUtil;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Prepares user-supplied deployment logs for processing. The original text is
 * validated, size-limited and redacted; the unsanitised input is never stored
 * and must never leave this method unredacted.
 */
@Component
public class LogSanitizer {

    public static final int MAX_CHARS = 100_000;
    public static final int MAX_LINES = 2_000;

    public static final String REDACTION_WARNING =
        "Automated redaction reduces risk but cannot guarantee every secret is caught — "
            + "review the preview before sharing it further.";

    private static final Pattern[] EXTRA_PATTERNS = {
        // AWS access keys and secrets
        Pattern.compile("\\b(AKIA|ASIA)[0-9A-Z]{16}\\b"),
        Pattern.compile("(?i)aws_secret_access_key\\s*[:=]\\s*\\S+"),
        // Google/Gemini API keys
        Pattern.compile("\\bAIza[0-9A-Za-z_\\-]{30,}\\b"),
        Pattern.compile("\\bAQ\\.[0-9A-Za-z_\\-]{30,}\\b"),
        // Cookie and authorization headers
        Pattern.compile("(?im)^(set-)?cookie\\s*:\\s*.+$"),
        Pattern.compile("(?im)^authorization\\s*:\\s*.+$"),
        Pattern.compile("(?i)(session|csrf|xsrf)[a-z_-]*=\\S{8,}"),
        // Connection strings with credentials embedded
        Pattern.compile("(?i)jdbc:[a-z]+://\\S+"),
        // Netlify/Render/Supabase style tokens
        Pattern.compile("\\b(nfp_|rnd_|sbp_)[0-9A-Za-z_\\-]{20,}\\b"),
    };

    public record Sanitized(String content, boolean truncated, int removedLines) {}

    /**
     * @throws IllegalArgumentException for binary or oversized input
     */
    public Sanitized sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Log content is required");
        }
        if (looksBinary(raw)) {
            throw new IllegalArgumentException("Binary content is not accepted — paste or upload a plain-text log");
        }
        boolean truncated = false;
        String text = raw;
        if (text.length() > MAX_CHARS) {
            text = text.substring(0, MAX_CHARS);
            truncated = true;
        }
        String[] lines = text.split("\n", -1);
        int removed = 0;
        if (lines.length > MAX_LINES) {
            removed = lines.length - MAX_LINES;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < MAX_LINES; i++) sb.append(lines[i]).append('\n');
            text = sb.toString();
            truncated = true;
        }
        String redacted = SecretRedactionUtil.redact(text);
        for (Pattern p : EXTRA_PATTERNS) {
            redacted = p.matcher(redacted).replaceAll("[REDACTED]");
        }
        return new Sanitized(redacted, truncated, removed);
    }

    private boolean looksBinary(String raw) {
        int sample = Math.min(raw.length(), 4_000);
        int control = 0;
        for (int i = 0; i < sample; i++) {
            char c = raw.charAt(i);
            if (c == 0) return true; // NUL byte: definitely binary
            if (c < 0x09 || (c > 0x0D && c < 0x20)) control++;
        }
        return sample > 0 && control * 100 / sample > 5;
    }
}

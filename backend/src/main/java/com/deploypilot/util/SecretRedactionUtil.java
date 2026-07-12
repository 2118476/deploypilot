package com.deploypilot.util;

import java.util.regex.Pattern;

public class SecretRedactionUtil {

    private static final Pattern[] SECRET_PATTERNS = {
        // API Keys
        Pattern.compile("(?i)(api[_-]?key|apikey)\\s*[:=]\\s*['\"]?[a-zA-Z0-9_\\-]{16,}['\"]?", Pattern.MULTILINE),
        // Passwords
        Pattern.compile("(?i)(password|passwd|pwd|secret)\\s*[:=]\\s*['\"]?[^\\s'\"]+['\"]?", Pattern.MULTILINE),
        // JWT tokens
        Pattern.compile("eyJ[a-zA-Z0-9_-]*\\.eyJ[a-zA-Z0-9_-]*\\.[a-zA-Z0-9_-]*"),
        // Bearer tokens
        Pattern.compile("(?i)bearer\\s+[a-zA-Z0-9_\\-\\.]{20,}"),
        // Database URLs with credentials
        Pattern.compile("(?i)(postgres|mysql|mongodb)://[^:]+:[^@]+@"),
        // Private keys
        Pattern.compile("-----BEGIN (RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----[\\s\\S]*?-----END (RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----"),
        // Firebase service account
        Pattern.compile("(?i)(private_key|client_email|\"private_key_id\")\\s*[:=]\\s*['\"][^'\"]+['\"]"),
        // GitHub tokens
        Pattern.compile("(?i)(ghp_[a-zA-Z0-9]{36}|gho_[a-zA-Z0-9]{36}|github_pat_[a-zA-Z0-9_]{22,})"),
        // Generic tokens
        Pattern.compile("(?i)(token|auth)\\s*[:=]\\s*['\"]?[a-zA-Z0-9_\\-]{20,}['\"]?", Pattern.MULTILINE),
        // Email credentials
        Pattern.compile("(?i)(smtp|imap)://[^:]+:[^@]+@"),
        // Connection strings
        Pattern.compile("(?i)(connection[_-]?string)\\s*[:=]\\s*['\"]?[^'\"]*password[^'\"]*['\"]?", Pattern.MULTILINE),
        // Supabase service role key
        Pattern.compile("(?i)(service[_-]?role[_-]?key)\\s*[:=]\\s*['\"]?[a-zA-Z0-9_\\-]{20,}['\"]?", Pattern.MULTILINE),
    };

    public static String redact(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String result = input;
        for (Pattern pattern : SECRET_PATTERNS) {
            result = pattern.matcher(result).replaceAll(matcher -> {
                String match = matcher.group();
                // Preserve the key/label part, redact the value
                int sepIdx = findSeparator(match);
                if (sepIdx > 0) {
                    String key = match.substring(0, sepIdx + 1);
                    return key + " [REDACTED]";
                }
                return "[REDACTED]";
            });
        }
        return result;
    }

    private static int findSeparator(String match) {
        for (int i = 0; i < match.length(); i++) {
            char c = match.charAt(i);
            if (c == ':' || c == '=' || c == ' ' || c == '/') {
                return i;
            }
        }
        return -1;
    }
}

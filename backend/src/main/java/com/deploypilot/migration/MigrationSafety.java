package com.deploypilot.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/**
 * Deterministic migration checksum and destructive-statement detection. Operates
 * only on repository-owned SQL — DeployPilot never generates migration SQL and
 * never invents rollback SQL. Potentially destructive migrations are flagged so
 * they can be blocked and left for manual expert review.
 */
public final class MigrationSafety {

    private MigrationSafety() {}

    private static final Pattern DROP_DATABASE = Pattern.compile("(?is)\\bDROP\\s+DATABASE\\b");
    private static final Pattern DROP_SCHEMA = Pattern.compile("(?is)\\bDROP\\s+SCHEMA\\b");
    private static final Pattern DROP_TABLE = Pattern.compile("(?is)\\bDROP\\s+TABLE\\b");
    private static final Pattern TRUNCATE = Pattern.compile("(?is)\\bTRUNCATE\\b");
    private static final Pattern ALTER_DROP = Pattern.compile("(?is)\\bALTER\\s+TABLE\\b[\\s\\S]*?\\bDROP\\s+(COLUMN|CONSTRAINT)\\b");
    private static final Pattern DELETE_FROM = Pattern.compile("(?is)\\bDELETE\\s+FROM\\b");
    private static final Pattern HAS_WHERE = Pattern.compile("(?is)\\bWHERE\\b");

    public record Verdict(boolean destructive, String reason) {}

    /** Detects potentially destructive statements. A DELETE without a WHERE is treated as destructive. */
    public static Verdict detect(String sql) {
        if (sql == null || sql.isBlank()) return new Verdict(false, null);
        String stripped = stripComments(sql);
        if (DROP_DATABASE.matcher(stripped).find()) return new Verdict(true, "Contains DROP DATABASE");
        if (DROP_SCHEMA.matcher(stripped).find()) return new Verdict(true, "Contains DROP SCHEMA");
        if (DROP_TABLE.matcher(stripped).find()) return new Verdict(true, "Contains DROP TABLE");
        if (TRUNCATE.matcher(stripped).find()) return new Verdict(true, "Contains TRUNCATE");
        if (ALTER_DROP.matcher(stripped).find()) return new Verdict(true, "Contains a destructive ALTER (DROP COLUMN/CONSTRAINT)");
        // A DELETE without a restrictive WHERE in the same statement is destructive.
        for (String statement : stripped.split(";")) {
            if (DELETE_FROM.matcher(statement).find() && !HAS_WHERE.matcher(statement).find()) {
                return new Verdict(true, "Contains a DELETE without a WHERE clause");
            }
        }
        return new Verdict(false, null);
    }

    /** SHA-256 hex checksum over the normalised SQL (line endings unified). */
    public static String checksum(String sql) {
        String normalised = (sql == null ? "" : sql).replace("\r\n", "\n").strip();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(normalised.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.substring(0, 64);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Removes -- line comments and /* *\/ block comments before scanning. */
    private static String stripComments(String sql) {
        String noBlock = sql.replaceAll("(?s)/\\*.*?\\*/", " ");
        return noBlock.replaceAll("(?m)--.*$", " ");
    }
}

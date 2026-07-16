package com.deploypilot.model.enums;

/** How the user wants DeployPilot to handle the database. */
public enum DatabaseChoice {
    /** Import connection details for an existing database (no creation). Default. */
    MANUAL,
    /** Use an existing Supabase project the user selects. */
    EXISTING_SUPABASE_PROJECT,
    /** Create a new Supabase project on the free plan. */
    CREATE_SUPABASE_PROJECT;

    public static DatabaseChoice parse(String s) {
        if (s == null || s.isBlank()) return MANUAL;
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown database choice: " + s);
        }
    }
}

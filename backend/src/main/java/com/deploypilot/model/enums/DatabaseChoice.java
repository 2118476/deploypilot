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
        String v = s.trim().toUpperCase();
        // Accept the shorthand values the UI/clients send.
        switch (v) {
            case "CREATE_NEW", "CREATE", "NEW" -> { return CREATE_SUPABASE_PROJECT; }
            case "USE_EXISTING", "EXISTING", "SELECT_EXISTING" -> { return EXISTING_SUPABASE_PROJECT; }
            default -> { }
        }
        try {
            return valueOf(v);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown database choice: " + s);
        }
    }
}

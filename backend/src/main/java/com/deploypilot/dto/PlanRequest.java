package com.deploypilot.dto;

import jakarta.validation.constraints.Size;

import java.util.HashMap;
import java.util.Map;

/**
 * Inputs for generating an action plan. All fields are optional: the mode
 * defaults to the safe GUIDE_ME, the branch to the repository default, and each
 * component defaults to creating a new resource unless an existing one is chosen.
 */
public class PlanRequest {

    @Size(max = 20)
    private String mode;

    @Size(max = 80)
    private String branch;

    /** componentId -> existing site/service id to reuse instead of creating one. */
    private Map<String, String> existingSites = new HashMap<>();

    /** componentId -> desired name for a newly created resource. */
    private Map<String, String> newSiteNames = new HashMap<>();

    // ----- Database (Supabase) choices (Stage 5). All optional; default MANUAL. -----

    /** MANUAL | EXISTING_SUPABASE_PROJECT | CREATE_SUPABASE_PROJECT */
    @Size(max = 40)
    private String databaseChoice;

    @Size(max = 120)
    private String supabaseOrgId;

    /** Existing project reference to reuse. */
    @Size(max = 120)
    private String supabaseProjectRef;

    /** Desired name when creating a new project. */
    @Size(max = 120)
    private String supabaseProjectName;

    @Size(max = 40)
    private String supabaseRegion;

    /** Requested tier; execution always enforces the free plan. */
    @Size(max = 20)
    private String supabasePlan;

    /** Whether to apply approved, safe, repository-owned migrations. */
    private boolean applyMigrations;

    public String getMode() { return mode; } public void setMode(String m) { this.mode = m; }
    public String getBranch() { return branch; } public void setBranch(String b) { this.branch = b; }
    public Map<String, String> getExistingSites() { return existingSites; } public void setExistingSites(Map<String, String> e) { this.existingSites = e == null ? new HashMap<>() : e; }
    public Map<String, String> getNewSiteNames() { return newSiteNames; } public void setNewSiteNames(Map<String, String> n) { this.newSiteNames = n == null ? new HashMap<>() : n; }
    public String getDatabaseChoice() { return databaseChoice; } public void setDatabaseChoice(String d) { this.databaseChoice = d; }
    public String getSupabaseOrgId() { return supabaseOrgId; } public void setSupabaseOrgId(String s) { this.supabaseOrgId = s; }
    public String getSupabaseProjectRef() { return supabaseProjectRef; } public void setSupabaseProjectRef(String s) { this.supabaseProjectRef = s; }
    public String getSupabaseProjectName() { return supabaseProjectName; } public void setSupabaseProjectName(String s) { this.supabaseProjectName = s; }
    public String getSupabaseRegion() { return supabaseRegion; } public void setSupabaseRegion(String s) { this.supabaseRegion = s; }
    public String getSupabasePlan() { return supabasePlan; } public void setSupabasePlan(String s) { this.supabasePlan = s; }
    public boolean isApplyMigrations() { return applyMigrations; } public void setApplyMigrations(boolean a) { this.applyMigrations = a; }
}

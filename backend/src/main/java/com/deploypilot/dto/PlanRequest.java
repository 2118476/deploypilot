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

    public String getMode() { return mode; } public void setMode(String m) { this.mode = m; }
    public String getBranch() { return branch; } public void setBranch(String b) { this.branch = b; }
    public Map<String, String> getExistingSites() { return existingSites; } public void setExistingSites(Map<String, String> e) { this.existingSites = e == null ? new HashMap<>() : e; }
    public Map<String, String> getNewSiteNames() { return newSiteNames; } public void setNewSiteNames(Map<String, String> n) { this.newSiteNames = n == null ? new HashMap<>() : n; }
}

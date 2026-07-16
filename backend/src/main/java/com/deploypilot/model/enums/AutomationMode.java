package com.deploypilot.model.enums;

/**
 * Controls how much DeployPilot does on the user's behalf. The default is
 * {@link #GUIDE_ME}, which never changes any external service.
 */
public enum AutomationMode {
    /** Explain every step; the user performs provider actions manually. */
    GUIDE_ME,
    /** Generate configuration, variable mappings and action plans only. No external changes. */
    PREPARE_FOR_ME,
    /** Perform only the actions the user has explicitly approved. */
    DEPLOY_FOR_ME
}

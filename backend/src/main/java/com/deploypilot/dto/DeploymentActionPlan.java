package com.deploypilot.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * An executable, human-reviewable deployment action plan generated from the
 * latest blueprint. Every action is classified and nothing here contains a
 * secret value. The plan is fingerprinted ({@code planHash}) so a confirmation
 * can be bound to exactly these actions.
 */
public class DeploymentActionPlan {

    public static final String CONSENT_NOTICE =
        "DeployPilot will only perform the actions shown below after you confirm.";

    private String repository;
    private String branch;
    private String commitSha;
    private String mode;
    private String planHash;
    private final String consentNotice = CONSENT_NOTICE;
    private boolean executable;                 // false in GUIDE_ME / PREPARE_FOR_ME
    private List<PlannedAction> actions = new ArrayList<>();
    private List<EnvVarPlanItem> environmentVariables = new ArrayList<>();
    private DatabaseHandoff database;
    private List<String> warnings = new ArrayList<>();
    private List<String> blockers = new ArrayList<>();

    /** A single classified action against a provider. Carries no secret values. */
    public static class PlannedAction {
        private String id;                      // stable key, e.g. "backend.create"
        private int order;                      // 1-based execution order
        private String type;                    // ActionType name
        private String provider;                // GITHUB | NETLIFY | RENDER | NONE
        private String account;                 // connected account label
        private String component;               // component name/id
        private String title;
        private String description;
        private String targetResource;          // existing resource name, or null for new
        private boolean createsNewResource;
        private boolean changesExisting;
        private boolean reversible;
        private boolean requiresRepositoryChange;
        private String costNote;                // set only when a paid plan could be involved
        private List<String> environmentVariableNames = new ArrayList<>();
        private List<String> dependsOn = new ArrayList<>();

        public String getId() { return id; } public void setId(String i) { this.id = i; }
        public int getOrder() { return order; } public void setOrder(int o) { this.order = o; }
        public String getType() { return type; } public void setType(String t) { this.type = t; }
        public String getProvider() { return provider; } public void setProvider(String p) { this.provider = p; }
        public String getAccount() { return account; } public void setAccount(String a) { this.account = a; }
        public String getComponent() { return component; } public void setComponent(String c) { this.component = c; }
        public String getTitle() { return title; } public void setTitle(String t) { this.title = t; }
        public String getDescription() { return description; } public void setDescription(String d) { this.description = d; }
        public String getTargetResource() { return targetResource; } public void setTargetResource(String t) { this.targetResource = t; }
        public boolean isCreatesNewResource() { return createsNewResource; } public void setCreatesNewResource(boolean c) { this.createsNewResource = c; }
        public boolean isChangesExisting() { return changesExisting; } public void setChangesExisting(boolean c) { this.changesExisting = c; }
        public boolean isReversible() { return reversible; } public void setReversible(boolean r) { this.reversible = r; }
        public boolean isRequiresRepositoryChange() { return requiresRepositoryChange; } public void setRequiresRepositoryChange(boolean r) { this.requiresRepositoryChange = r; }
        public String getCostNote() { return costNote; } public void setCostNote(String c) { this.costNote = c; }
        public List<String> getEnvironmentVariableNames() { return environmentVariableNames; } public void setEnvironmentVariableNames(List<String> e) { this.environmentVariableNames = e; }
        public List<String> getDependsOn() { return dependsOn; } public void setDependsOn(List<String> d) { this.dependsOn = d; }
    }

    /** Environment-variable planning row. Never carries the value itself. */
    public static class EnvVarPlanItem {
        private String name;
        private String destination;             // where the value will be set
        private String source;                  // where the value comes from
        private boolean required;
        private boolean secret;                 // masked in the UI
        private boolean generatable;            // DeployPilot can generate it (app secret)
        private String valueStatus;             // READY | NEEDS_INPUT | WILL_BE_GENERATED | FROM_PREVIOUS_STEP

        public String getName() { return name; } public void setName(String n) { this.name = n; }
        public String getDestination() { return destination; } public void setDestination(String d) { this.destination = d; }
        public String getSource() { return source; } public void setSource(String s) { this.source = s; }
        public boolean isRequired() { return required; } public void setRequired(boolean r) { this.required = r; }
        public boolean isSecret() { return secret; } public void setSecret(boolean s) { this.secret = s; }
        public boolean isGeneratable() { return generatable; } public void setGeneratable(boolean g) { this.generatable = g; }
        public String getValueStatus() { return valueStatus; } public void setValueStatus(String v) { this.valueStatus = v; }
    }

    /** Database handoff instructions. Import-only in this stage; no creation. */
    public static class DatabaseHandoff {
        private boolean required;
        private String detectedProvider;        // Supabase | Render PostgreSQL | ...
        private boolean connectionSupplied;
        private List<String> requiredFields = new ArrayList<>();
        private String instructions;

        public boolean isRequired() { return required; } public void setRequired(boolean r) { this.required = r; }
        public String getDetectedProvider() { return detectedProvider; } public void setDetectedProvider(String d) { this.detectedProvider = d; }
        public boolean isConnectionSupplied() { return connectionSupplied; } public void setConnectionSupplied(boolean c) { this.connectionSupplied = c; }
        public List<String> getRequiredFields() { return requiredFields; } public void setRequiredFields(List<String> r) { this.requiredFields = r; }
        public String getInstructions() { return instructions; } public void setInstructions(String i) { this.instructions = i; }
    }

    public String getRepository() { return repository; } public void setRepository(String r) { this.repository = r; }
    public String getBranch() { return branch; } public void setBranch(String b) { this.branch = b; }
    public String getCommitSha() { return commitSha; } public void setCommitSha(String c) { this.commitSha = c; }
    public String getMode() { return mode; } public void setMode(String m) { this.mode = m; }
    public String getPlanHash() { return planHash; } public void setPlanHash(String p) { this.planHash = p; }
    public String getConsentNotice() { return consentNotice; }
    public boolean isExecutable() { return executable; } public void setExecutable(boolean e) { this.executable = e; }
    public List<PlannedAction> getActions() { return actions; } public void setActions(List<PlannedAction> a) { this.actions = a; }
    public List<EnvVarPlanItem> getEnvironmentVariables() { return environmentVariables; } public void setEnvironmentVariables(List<EnvVarPlanItem> e) { this.environmentVariables = e; }
    public DatabaseHandoff getDatabase() { return database; } public void setDatabase(DatabaseHandoff d) { this.database = d; }
    public List<String> getWarnings() { return warnings; } public void setWarnings(List<String> w) { this.warnings = w; }
    public List<String> getBlockers() { return blockers; } public void setBlockers(List<String> b) { this.blockers = b; }
}

package com.deploypilot.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic deployment blueprint generated from a stored repository
 * analysis. Contains recommendations, evidence and placeholders only —
 * never real secret values and never invented URLs.
 */
public class BlueprintResult {
    private String repository;
    private String structure;
    private String rulesVersion;
    private List<Component> components = new ArrayList<>();
    private List<Relationship> relationships = new ArrayList<>();
    private List<EnvVarMapping> environmentVariables = new ArrayList<>();
    private List<Finding> findings = new ArrayList<>();
    private List<Step> steps = new ArrayList<>();
    private List<FilePreview> filePreviews = new ArrayList<>();

    public static class PlatformOption {
        private String platform;
        private String reason;
        private List<String> evidence = new ArrayList<>();
        private String confidence;          // HIGH | MEDIUM | LOW
        private boolean requiresConfirmation;
        private String freeTierNote;
        private String coldStartNote;
        private String pricingNote;

        public String getPlatform() { return platform; } public void setPlatform(String p) { this.platform = p; }
        public String getReason() { return reason; } public void setReason(String r) { this.reason = r; }
        public List<String> getEvidence() { return evidence; } public void setEvidence(List<String> e) { this.evidence = e; }
        public String getConfidence() { return confidence; } public void setConfidence(String c) { this.confidence = c; }
        public boolean isRequiresConfirmation() { return requiresConfirmation; }
        public void setRequiresConfirmation(boolean r) { this.requiresConfirmation = r; }
        public String getFreeTierNote() { return freeTierNote; } public void setFreeTierNote(String f) { this.freeTierNote = f; }
        public String getColdStartNote() { return coldStartNote; } public void setColdStartNote(String c) { this.coldStartNote = c; }
        public String getPricingNote() { return pricingNote; } public void setPricingNote(String p) { this.pricingNote = p; }
    }

    public static class Component {
        private String id;                  // e.g. "frontend@frontend", "backend@backend", "database@postgresql"
        private String type;                // FRONTEND | BACKEND | DATABASE | EXTERNAL_SERVICE
        private String name;                // e.g. "React", "Spring Boot", "PostgreSQL"
        private String path;                // repository path ("" = root, null for non-code components)
        private String runtime;             // e.g. "Java 17", "Node.js", null
        private String buildTool;           // e.g. "Vite", "Maven"
        private PlatformOption recommendedPlatform;
        private List<PlatformOption> alternatives = new ArrayList<>();
        private String selectedPlatform;    // recommended unless overridden by the user
        private String buildCommand;
        private String startCommand;
        private String rootDirectory;
        private String publishDirectory;    // frontend only
        private String healthCheckPath;     // backend only, null when unknown
        private List<String> notes = new ArrayList<>();

        public String getId() { return id; } public void setId(String i) { this.id = i; }
        public String getType() { return type; } public void setType(String t) { this.type = t; }
        public String getName() { return name; } public void setName(String n) { this.name = n; }
        public String getPath() { return path; } public void setPath(String p) { this.path = p; }
        public String getRuntime() { return runtime; } public void setRuntime(String r) { this.runtime = r; }
        public String getBuildTool() { return buildTool; } public void setBuildTool(String b) { this.buildTool = b; }
        public PlatformOption getRecommendedPlatform() { return recommendedPlatform; }
        public void setRecommendedPlatform(PlatformOption p) { this.recommendedPlatform = p; }
        public List<PlatformOption> getAlternatives() { return alternatives; }
        public void setAlternatives(List<PlatformOption> a) { this.alternatives = a; }
        public String getSelectedPlatform() { return selectedPlatform; } public void setSelectedPlatform(String s) { this.selectedPlatform = s; }
        public String getBuildCommand() { return buildCommand; } public void setBuildCommand(String b) { this.buildCommand = b; }
        public String getStartCommand() { return startCommand; } public void setStartCommand(String s) { this.startCommand = s; }
        public String getRootDirectory() { return rootDirectory; } public void setRootDirectory(String r) { this.rootDirectory = r; }
        public String getPublishDirectory() { return publishDirectory; } public void setPublishDirectory(String p) { this.publishDirectory = p; }
        public String getHealthCheckPath() { return healthCheckPath; } public void setHealthCheckPath(String h) { this.healthCheckPath = h; }
        public List<String> getNotes() { return notes; } public void setNotes(List<String> n) { this.notes = n; }
    }

    public static class Relationship {
        private String fromComponent;
        private String toComponent;
        private String description;
        private String viaVariable;        // env var that carries the dependency, nullable

        public Relationship() {}
        public Relationship(String from, String to, String description, String viaVariable) {
            this.fromComponent = from; this.toComponent = to;
            this.description = description; this.viaVariable = viaVariable;
        }
        public String getFromComponent() { return fromComponent; } public void setFromComponent(String f) { this.fromComponent = f; }
        public String getToComponent() { return toComponent; } public void setToComponent(String t) { this.toComponent = t; }
        public String getDescription() { return description; } public void setDescription(String d) { this.description = d; }
        public String getViaVariable() { return viaVariable; } public void setViaVariable(String v) { this.viaVariable = v; }
    }

    public static class EnvVarMapping {
        private String name;
        private String componentId;
        private String targetPlatform;
        private String classification;      // SECRET_OR_SENSITIVE | PUBLIC_CONFIGURATION | CONFIGURATION
        private Boolean required;           // null = unknown
        private String valueSource;         // human description of where the value comes from
        private String expectedFormat;      // e.g. "${BACKEND_PUBLIC_URL}"
        private boolean generatable;        // true only for app-owned secrets DeployPilot may generate client-side
        private String dependsOnOutput;     // e.g. "BACKEND_PUBLIC_URL", null if independent
        private String sourceEvidence;      // file the name was found in

        public String getName() { return name; } public void setName(String n) { this.name = n; }
        public String getComponentId() { return componentId; } public void setComponentId(String c) { this.componentId = c; }
        public String getTargetPlatform() { return targetPlatform; } public void setTargetPlatform(String t) { this.targetPlatform = t; }
        public String getClassification() { return classification; } public void setClassification(String c) { this.classification = c; }
        public Boolean getRequired() { return required; } public void setRequired(Boolean r) { this.required = r; }
        public String getValueSource() { return valueSource; } public void setValueSource(String v) { this.valueSource = v; }
        public String getExpectedFormat() { return expectedFormat; } public void setExpectedFormat(String e) { this.expectedFormat = e; }
        public boolean isGeneratable() { return generatable; } public void setGeneratable(boolean g) { this.generatable = g; }
        public String getDependsOnOutput() { return dependsOnOutput; } public void setDependsOnOutput(String d) { this.dependsOnOutput = d; }
        public String getSourceEvidence() { return sourceEvidence; } public void setSourceEvidence(String s) { this.sourceEvidence = s; }
    }

    public static class Finding {
        private String severity;            // BLOCKER | WARNING | INFORMATIONAL
        private String title;
        private String detail;
        private String evidence;
        private String affectedFile;        // nullable
        private String proposedFix;
        private boolean requiresConfirmation;

        public String getSeverity() { return severity; } public void setSeverity(String s) { this.severity = s; }
        public String getTitle() { return title; } public void setTitle(String t) { this.title = t; }
        public String getDetail() { return detail; } public void setDetail(String d) { this.detail = d; }
        public String getEvidence() { return evidence; } public void setEvidence(String e) { this.evidence = e; }
        public String getAffectedFile() { return affectedFile; } public void setAffectedFile(String a) { this.affectedFile = a; }
        public String getProposedFix() { return proposedFix; } public void setProposedFix(String p) { this.proposedFix = p; }
        public boolean isRequiresConfirmation() { return requiresConfirmation; }
        public void setRequiresConfirmation(boolean r) { this.requiresConfirmation = r; }
    }

    public static class Step {
        private int index;                  // 1-based
        private String title;
        private String what;
        private String where;
        private List<String> inputs = new ArrayList<>();
        private String produces;            // output key like "BACKEND_PUBLIC_URL", nullable
        private List<String> unlocksVariables = new ArrayList<>();
        private String expectedResult;
        private List<Integer> blockedBy = new ArrayList<>();

        public int getIndex() { return index; } public void setIndex(int i) { this.index = i; }
        public String getTitle() { return title; } public void setTitle(String t) { this.title = t; }
        public String getWhat() { return what; } public void setWhat(String w) { this.what = w; }
        public String getWhere() { return where; } public void setWhere(String w) { this.where = w; }
        public List<String> getInputs() { return inputs; } public void setInputs(List<String> i) { this.inputs = i; }
        public String getProduces() { return produces; } public void setProduces(String p) { this.produces = p; }
        public List<String> getUnlocksVariables() { return unlocksVariables; }
        public void setUnlocksVariables(List<String> u) { this.unlocksVariables = u; }
        public String getExpectedResult() { return expectedResult; } public void setExpectedResult(String e) { this.expectedResult = e; }
        public List<Integer> getBlockedBy() { return blockedBy; } public void setBlockedBy(List<Integer> b) { this.blockedBy = b; }
    }

    public static class FilePreview {
        private String path;
        private String purpose;
        private Boolean exists;             // null = unknown
        private String currentContent;      // relevant excerpt when the file exists and could be read
        private String suggestedContent;
        private String diff;                // simple line diff when both sides are known
        private String reason;

        public String getPath() { return path; } public void setPath(String p) { this.path = p; }
        public String getPurpose() { return purpose; } public void setPurpose(String p) { this.purpose = p; }
        public Boolean getExists() { return exists; } public void setExists(Boolean e) { this.exists = e; }
        public String getCurrentContent() { return currentContent; } public void setCurrentContent(String c) { this.currentContent = c; }
        public String getSuggestedContent() { return suggestedContent; } public void setSuggestedContent(String s) { this.suggestedContent = s; }
        public String getDiff() { return diff; } public void setDiff(String d) { this.diff = d; }
        public String getReason() { return reason; } public void setReason(String r) { this.reason = r; }
    }

    public String getRepository() { return repository; } public void setRepository(String r) { this.repository = r; }
    public String getStructure() { return structure; } public void setStructure(String s) { this.structure = s; }
    public String getRulesVersion() { return rulesVersion; } public void setRulesVersion(String r) { this.rulesVersion = r; }
    public List<Component> getComponents() { return components; } public void setComponents(List<Component> c) { this.components = c; }
    public List<Relationship> getRelationships() { return relationships; } public void setRelationships(List<Relationship> r) { this.relationships = r; }
    public List<EnvVarMapping> getEnvironmentVariables() { return environmentVariables; }
    public void setEnvironmentVariables(List<EnvVarMapping> e) { this.environmentVariables = e; }
    public List<Finding> getFindings() { return findings; } public void setFindings(List<Finding> f) { this.findings = f; }
    public List<Step> getSteps() { return steps; } public void setSteps(List<Step> s) { this.steps = s; }
    public List<FilePreview> getFilePreviews() { return filePreviews; } public void setFilePreviews(List<FilePreview> f) { this.filePreviews = f; }
}

package com.deploypilot.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic analysis result for one repository. Contains only technology
 * facts, evidence and environment-variable NAMES — never secret values.
 */
public class StackDetectionResult {
    private String repository;
    private String structure; // MONOREPO | SINGLE_APPLICATION | UNKNOWN
    private List<Detection> detections = new ArrayList<>();
    private List<EnvVarFinding> environmentVariables = new ArrayList<>();
    private List<String> buildCommands = new ArrayList<>();
    private List<String> startCommands = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private List<String> analyzedFiles = new ArrayList<>();
    private List<String> skippedFiles = new ArrayList<>();

    public static class Detection {
        private String category;   // FRONTEND_FRAMEWORK, BACKEND_FRAMEWORK, LANGUAGE, BUILD_TOOL,
                                    // PACKAGE_MANAGER, DATABASE, CONTAINER, HOSTING, EXTERNAL_SERVICE
        private String name;        // e.g. "React", "Spring Boot", "PostgreSQL"
        private String path;        // where in the repo the evidence lives, "" for root
        private String confidence;  // HIGH | MEDIUM | LOW
        private List<String> evidence = new ArrayList<>();

        public Detection() {}
        public Detection(String category, String name, String path, String confidence, List<String> evidence) {
            this.category = category; this.name = name; this.path = path;
            this.confidence = confidence; this.evidence = evidence;
        }
        public String getCategory() { return category; } public void setCategory(String c) { this.category = c; }
        public String getName() { return name; } public void setName(String n) { this.name = n; }
        public String getPath() { return path; } public void setPath(String p) { this.path = p; }
        public String getConfidence() { return confidence; } public void setConfidence(String c) { this.confidence = c; }
        public List<String> getEvidence() { return evidence; } public void setEvidence(List<String> e) { this.evidence = e; }
    }

    public static class EnvVarFinding {
        private String name;
        private String classification; // SECRET_OR_SENSITIVE | PUBLIC_CONFIGURATION | CONFIGURATION
        private String source;

        public EnvVarFinding() {}
        public EnvVarFinding(String name, String classification, String source) {
            this.name = name; this.classification = classification; this.source = source;
        }
        public String getName() { return name; } public void setName(String n) { this.name = n; }
        public String getClassification() { return classification; } public void setClassification(String c) { this.classification = c; }
        public String getSource() { return source; } public void setSource(String s) { this.source = s; }
    }

    public String getRepository() { return repository; } public void setRepository(String r) { this.repository = r; }
    public String getStructure() { return structure; } public void setStructure(String s) { this.structure = s; }
    public List<Detection> getDetections() { return detections; } public void setDetections(List<Detection> d) { this.detections = d; }
    public List<EnvVarFinding> getEnvironmentVariables() { return environmentVariables; }
    public void setEnvironmentVariables(List<EnvVarFinding> e) { this.environmentVariables = e; }
    public List<String> getBuildCommands() { return buildCommands; } public void setBuildCommands(List<String> b) { this.buildCommands = b; }
    public List<String> getStartCommands() { return startCommands; } public void setStartCommands(List<String> s) { this.startCommands = s; }
    public List<String> getWarnings() { return warnings; } public void setWarnings(List<String> w) { this.warnings = w; }
    public List<String> getAnalyzedFiles() { return analyzedFiles; } public void setAnalyzedFiles(List<String> a) { this.analyzedFiles = a; }
    public List<String> getSkippedFiles() { return skippedFiles; } public void setSkippedFiles(List<String> s) { this.skippedFiles = s; }
}

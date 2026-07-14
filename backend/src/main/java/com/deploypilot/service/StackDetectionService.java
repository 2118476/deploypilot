package com.deploypilot.service;

import com.deploypilot.dto.StackDetectionResult;
import com.deploypilot.dto.StackDetectionResult.Detection;
import com.deploypilot.dto.StackDetectionResult.EnvVarFinding;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, rule-based technology detection. Works entirely on file
 * contents handed to it — performs no network access and never calls AI.
 */
@Service
public class StackDetectionService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Pattern ENV_LINE = Pattern.compile("^\\s*(?:export\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*=");
    private static final Pattern SECRET_NAME = Pattern.compile(
        "(?i)(secret|token|key|password|passwd|private|credential|auth|dsn|salt)|(?i)^database_url$|(?i)connection_string");
    private static final Pattern PUBLIC_PREFIX = Pattern.compile("^(VITE_|NEXT_PUBLIC_|REACT_APP_|PUBLIC_|EXPO_PUBLIC_)");
    private static final Pattern JAVA_VERSION = Pattern.compile("<java\\.version>\\s*(\\d+)\\s*</java\\.version>");

    /**
     * @param repository   owner/name, used only for labelling the result
     * @param allPaths     every file path in the repository listing
     * @param filesByPath  contents of the configuration files that were downloaded
     * @param skippedFiles files that were deliberately not downloaded (with reasons)
     * @param warnings     warnings accumulated while fetching (e.g. truncated listing)
     */
    public StackDetectionResult detect(String repository,
                                       List<String> allPaths,
                                       Map<String, String> filesByPath,
                                       List<String> skippedFiles,
                                       List<String> warnings) {
        StackDetectionResult result = new StackDetectionResult();
        result.setRepository(repository);
        result.getWarnings().addAll(warnings);
        result.getSkippedFiles().addAll(skippedFiles);
        result.getAnalyzedFiles().addAll(new TreeMap<>(filesByPath).keySet());

        Set<String> appRoots = new LinkedHashSet<>();

        for (Map.Entry<String, String> entry : new TreeMap<>(filesByPath).entrySet()) {
            String path = entry.getKey();
            String content = entry.getValue();
            String fileName = fileName(path);
            String dir = dirOf(path);

            switch (fileName) {
                case "package.json" -> { analyzePackageJson(path, dir, content, result); appRoots.add(dir); }
                case "pom.xml" -> { analyzePomXml(path, dir, content, result); appRoots.add(dir); }
                case "build.gradle", "build.gradle.kts" -> { analyzeGradle(path, dir, content, result); appRoots.add(dir); }
                case "requirements.txt", "pyproject.toml", "Pipfile" -> { analyzePython(path, dir, content, result); appRoots.add(dir); }
                case "application.properties", "application.yml", "application.yaml" ->
                    analyzeSpringConfig(path, content, result);
                case "Dockerfile" -> add(result, "CONTAINER", "Docker", dir, "HIGH", path + " exists");
                case "docker-compose.yml", "docker-compose.yaml" -> analyzeDockerCompose(path, content, result);
                case "netlify.toml" -> add(result, "HOSTING", "Netlify", dir, "HIGH", path + " exists");
                case "vercel.json" -> add(result, "HOSTING", "Vercel", dir, "HIGH", path + " exists");
                case "render.yaml" -> add(result, "HOSTING", "Render", dir, "HIGH", path + " exists");
                case "Procfile" -> add(result, "HOSTING", "Heroku (Procfile)", dir, "MEDIUM", path + " exists");
                case "firebase.json" -> add(result, "EXTERNAL_SERVICE", "Firebase", dir, "HIGH", path + " exists");
                case "angular.json" -> add(result, "FRONTEND_FRAMEWORK", "Angular", dir, "HIGH", path + " exists");
                default -> { /* env examples and vite/next configs handled below */ }
            }

            if (isEnvExample(fileName)) {
                analyzeEnvExample(path, content, result);
            }
            if (fileName.startsWith("vite.config.")) {
                add(result, "BUILD_TOOL", "Vite", dir, "HIGH", path + " exists");
            }
            if (fileName.startsWith("next.config.")) {
                add(result, "FRONTEND_FRAMEWORK", "Next.js", dir, "HIGH", path + " exists");
            }
            if (path.equals("supabase/config.toml") || path.endsWith("/supabase/config.toml")) {
                add(result, "EXTERNAL_SERVICE", "Supabase", dir, "HIGH", path + " exists");
            }
        }

        // Package-manager evidence comes from lockfile paths (no download needed)
        for (String path : allPaths) {
            String fileName = fileName(path);
            String dir = dirOf(path);
            switch (fileName) {
                case "package-lock.json" -> add(result, "PACKAGE_MANAGER", "npm", dir, "HIGH", path + " exists");
                case "yarn.lock" -> add(result, "PACKAGE_MANAGER", "Yarn", dir, "HIGH", path + " exists");
                case "pnpm-lock.yaml" -> add(result, "PACKAGE_MANAGER", "pnpm", dir, "HIGH", path + " exists");
                case "mvnw" -> add(result, "BUILD_TOOL", "Maven Wrapper", dir, "HIGH", path + " exists");
                case "gradlew" -> add(result, "BUILD_TOOL", "Gradle Wrapper", dir, "HIGH", path + " exists");
                case "tsconfig.json" -> add(result, "LANGUAGE", "TypeScript", dir, "HIGH", path + " exists");
                default -> { }
            }
        }

        result.setStructure(decideStructure(appRoots));
        if (result.getDetections().isEmpty()) {
            result.getWarnings().add("No recognizable technology evidence found. "
                + "The repository may use a stack DeployPilot does not detect yet.");
        }
        return result;
    }

    // ---------- individual analyzers ----------

    private void analyzePackageJson(String path, String dir, String content, StackDetectionResult result) {
        JsonNode root;
        try {
            root = JSON.readTree(content);
        } catch (Exception e) {
            result.getWarnings().add(path + " could not be parsed as JSON");
            return;
        }
        JsonNode deps = root.path("dependencies");
        JsonNode devDeps = root.path("devDependencies");

        if (deps.has("next")) {
            add(result, "FRONTEND_FRAMEWORK", "Next.js", dir, "HIGH", path + " depends on next");
        } else if (deps.has("react")) {
            add(result, "FRONTEND_FRAMEWORK", "React", dir, "HIGH", path + " depends on react");
        }
        if (deps.has("vue")) add(result, "FRONTEND_FRAMEWORK", "Vue", dir, "HIGH", path + " depends on vue");
        if (deps.has("svelte") || devDeps.has("svelte")) add(result, "FRONTEND_FRAMEWORK", "Svelte", dir, "HIGH", path + " depends on svelte");
        if (deps.has("@angular/core")) add(result, "FRONTEND_FRAMEWORK", "Angular", dir, "HIGH", path + " depends on @angular/core");

        if (deps.has("express")) add(result, "BACKEND_FRAMEWORK", "Express (Node.js)", dir, "HIGH", path + " depends on express");
        if (deps.has("@nestjs/core")) add(result, "BACKEND_FRAMEWORK", "NestJS (Node.js)", dir, "HIGH", path + " depends on @nestjs/core");
        if (deps.has("fastify")) add(result, "BACKEND_FRAMEWORK", "Fastify (Node.js)", dir, "HIGH", path + " depends on fastify");

        if (devDeps.has("vite") || deps.has("vite")) add(result, "BUILD_TOOL", "Vite", dir, "HIGH", path + " depends on vite");
        if (devDeps.has("typescript") || deps.has("typescript")) {
            add(result, "LANGUAGE", "TypeScript", dir, "HIGH", path + " depends on typescript");
        } else {
            add(result, "LANGUAGE", "JavaScript", dir, "MEDIUM", path + " exists without a typescript dependency");
        }

        if (deps.has("pg")) add(result, "DATABASE", "PostgreSQL", dir, "MEDIUM", path + " depends on pg");
        if (deps.has("mysql2") || deps.has("mysql")) add(result, "DATABASE", "MySQL", dir, "MEDIUM", path + " depends on mysql client");
        if (deps.has("mongoose") || deps.has("mongodb")) add(result, "DATABASE", "MongoDB", dir, "MEDIUM", path + " depends on a MongoDB client");
        if (deps.has("@supabase/supabase-js")) add(result, "EXTERNAL_SERVICE", "Supabase", dir, "HIGH", path + " depends on @supabase/supabase-js");
        if (deps.has("firebase") || deps.has("firebase-admin")) add(result, "EXTERNAL_SERVICE", "Firebase", dir, "HIGH", path + " depends on firebase");
        if (deps.has("stripe")) add(result, "EXTERNAL_SERVICE", "Stripe", dir, "HIGH", path + " depends on stripe");

        JsonNode scripts = root.path("scripts");
        String prefix = dir.isEmpty() ? "" : "cd " + dir + " && ";
        if (scripts.has("build")) result.getBuildCommands().add(prefix + "npm run build");
        if (scripts.has("start")) result.getStartCommands().add(prefix + "npm run start");
        else if (scripts.has("dev")) result.getStartCommands().add(prefix + "npm run dev");
    }

    private void analyzePomXml(String path, String dir, String content, StackDetectionResult result) {
        if (content.contains("spring-boot-starter-parent") || content.contains("spring-boot-starter")) {
            add(result, "BACKEND_FRAMEWORK", "Spring Boot", dir, "HIGH", path + " uses spring-boot-starter");
        }
        Matcher javaVersion = JAVA_VERSION.matcher(content);
        boolean versionDeclared = javaVersion.find();
        String java = versionDeclared ? "Java " + javaVersion.group(1) : "Java";
        add(result, "LANGUAGE", java, dir, versionDeclared ? "HIGH" : "MEDIUM",
            path + (versionDeclared ? " declares java.version" : " exists"));
        add(result, "BUILD_TOOL", "Maven", dir, "HIGH", path + " exists");

        if (content.contains("<artifactId>postgresql</artifactId>")) {
            add(result, "DATABASE", "PostgreSQL", dir, "HIGH", "PostgreSQL JDBC dependency found in " + path);
        }
        if (content.contains("mysql-connector")) {
            add(result, "DATABASE", "MySQL", dir, "HIGH", "MySQL JDBC dependency found in " + path);
        }
        if (content.contains("flyway-core")) {
            add(result, "EXTERNAL_SERVICE", "Flyway migrations", dir, "HIGH", "flyway-core dependency found in " + path);
        }

        String prefix = dir.isEmpty() ? "" : "cd " + dir + " && ";
        result.getBuildCommands().add(prefix + "mvn clean package");
        result.getStartCommands().add(prefix + "java -jar target/*.jar");
    }

    private void analyzeGradle(String path, String dir, String content, StackDetectionResult result) {
        add(result, "BUILD_TOOL", "Gradle", dir, "HIGH", path + " exists");
        if (content.contains("org.springframework.boot")) {
            add(result, "BACKEND_FRAMEWORK", "Spring Boot", dir, "HIGH", path + " applies the Spring Boot plugin");
        }
        if (content.contains("org.jetbrains.kotlin")) {
            add(result, "LANGUAGE", "Kotlin", dir, "HIGH", path + " applies a Kotlin plugin");
        } else {
            add(result, "LANGUAGE", "Java", dir, "MEDIUM", path + " exists");
        }
        String prefix = dir.isEmpty() ? "" : "cd " + dir + " && ";
        result.getBuildCommands().add(prefix + "gradle build");
    }

    private void analyzePython(String path, String dir, String content, StackDetectionResult result) {
        add(result, "LANGUAGE", "Python", dir, "HIGH", path + " exists");
        String lower = content.toLowerCase();
        if (lower.contains("django")) add(result, "BACKEND_FRAMEWORK", "Django", dir, "HIGH", path + " lists django");
        if (lower.contains("flask")) add(result, "BACKEND_FRAMEWORK", "Flask", dir, "HIGH", path + " lists flask");
        if (lower.contains("fastapi")) add(result, "BACKEND_FRAMEWORK", "FastAPI", dir, "HIGH", path + " lists fastapi");
        if (lower.contains("psycopg")) add(result, "DATABASE", "PostgreSQL", dir, "MEDIUM", path + " lists psycopg");
    }

    private void analyzeSpringConfig(String path, String content, StackDetectionResult result) {
        String dir = dirOf(path);
        if (content.contains("jdbc:postgresql")) {
            add(result, "DATABASE", "PostgreSQL", dir, "HIGH", path + " configures a jdbc:postgresql datasource");
        }
        if (content.contains("jdbc:mysql")) {
            add(result, "DATABASE", "MySQL", dir, "HIGH", path + " configures a jdbc:mysql datasource");
        }
        if (content.contains("mongodb")) {
            add(result, "DATABASE", "MongoDB", dir, "MEDIUM", path + " references mongodb");
        }
        // Collect ${VAR} style environment references (names only)
        Matcher m = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]+)(?::[^}]*)?}").matcher(content);
        while (m.find()) {
            addEnvVar(result, m.group(1), path);
        }
    }

    private void analyzeDockerCompose(String path, String content, StackDetectionResult result) {
        String dir = dirOf(path);
        add(result, "CONTAINER", "Docker Compose", dir, "HIGH", path + " exists");
        String lower = content.toLowerCase();
        if (lower.contains("image: postgres") || lower.contains("image: \"postgres")) {
            add(result, "DATABASE", "PostgreSQL", dir, "HIGH", path + " runs a postgres image");
        }
        if (lower.contains("image: mysql")) add(result, "DATABASE", "MySQL", dir, "HIGH", path + " runs a mysql image");
        if (lower.contains("image: mongo")) add(result, "DATABASE", "MongoDB", dir, "HIGH", path + " runs a mongo image");
        if (lower.contains("image: redis")) add(result, "EXTERNAL_SERVICE", "Redis", dir, "HIGH", path + " runs a redis image");
    }

    private void analyzeEnvExample(String path, String content, StackDetectionResult result) {
        for (String line : content.split("\n")) {
            Matcher m = ENV_LINE.matcher(line);
            if (m.find()) {
                addEnvVar(result, m.group(1), path);
            }
        }
    }

    private void addEnvVar(StackDetectionResult result, String name, String source) {
        boolean exists = result.getEnvironmentVariables().stream()
            .anyMatch(v -> v.getName().equals(name) && v.getSource().equals(source));
        if (exists) return;
        String classification;
        if (SECRET_NAME.matcher(name).find()) {
            classification = "SECRET_OR_SENSITIVE";
        } else if (PUBLIC_PREFIX.matcher(name).find()) {
            classification = "PUBLIC_CONFIGURATION";
        } else {
            classification = "CONFIGURATION";
        }
        result.getEnvironmentVariables().add(new EnvVarFinding(name, classification, source));
    }

    private String decideStructure(Set<String> appRoots) {
        if (appRoots.isEmpty()) return "UNKNOWN";
        Set<String> topLevel = new LinkedHashSet<>();
        for (String root : appRoots) {
            topLevel.add(root.isEmpty() ? "" : root.split("/")[0]);
        }
        return topLevel.size() > 1 ? "MONOREPO" : "SINGLE_APPLICATION";
    }

    // ---------- helpers ----------

    private void add(StackDetectionResult result, String category, String name,
                     String path, String confidence, String evidence) {
        for (Detection d : result.getDetections()) {
            if (d.getCategory().equals(category) && d.getName().equals(name)
                && d.getPath().equals(path)) {
                if (!d.getEvidence().contains(evidence)) d.getEvidence().add(evidence);
                if ("HIGH".equals(confidence)) d.setConfidence("HIGH");
                return;
            }
        }
        List<String> evidenceList = new ArrayList<>();
        evidenceList.add(evidence);
        result.getDetections().add(new Detection(category, name, path, confidence, evidenceList));
    }

    private boolean isEnvExample(String fileName) {
        String lower = fileName.toLowerCase();
        // Explicitly never treat a real .env (or .env.local etc.) as analyzable
        return lower.equals(".env.example") || lower.equals(".env.sample")
            || lower.equals(".env.template") || lower.equals("env.example")
            || lower.endsWith(".env.example");
    }

    private String fileName(String path) {
        int idx = path.lastIndexOf('/');
        return idx < 0 ? path : path.substring(idx + 1);
    }

    private String dirOf(String path) {
        int idx = path.lastIndexOf('/');
        return idx < 0 ? "" : path.substring(0, idx);
    }
}

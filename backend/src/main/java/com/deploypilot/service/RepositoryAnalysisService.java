package com.deploypilot.service;

import com.deploypilot.dto.RepositoryAnalysisResponse;
import com.deploypilot.dto.StackDetectionResult;
import com.deploypilot.exception.ResourceNotFoundException;
import com.deploypilot.exception.UnauthorizedAccessException;
import com.deploypilot.model.Project;
import com.deploypilot.model.RepositoryAnalysis;
import com.deploypilot.model.enums.AnalysisStatus;
import com.deploypilot.repoaccess.RepositoryAccessException;
import com.deploypilot.repoaccess.RepositoryFileEntry;
import com.deploypilot.repoaccess.RepositoryFileReader;
import com.deploypilot.repoaccess.RepositoryMetadata;
import com.deploypilot.repoaccess.RepositoryRef;
import com.deploypilot.repository.ProjectRepository;
import com.deploypilot.repository.RepositoryAnalysisRepository;
import com.deploypilot.util.CurrentUserUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Orchestrates read-only repository analysis: ownership checks, safe file
 * selection with hard limits, deterministic stack detection and persistence.
 */
@Service
public class RepositoryAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryAnalysisService.class);

    // ---- safety limits (Part F) ----
    static final int MAX_TREE_ENTRIES = 20_000;       // listing larger than this is refused
    static final int MAX_FILES_TO_FETCH = 30;         // config files actually downloaded
    static final int MAX_FILE_BYTES = 200 * 1024;     // per file
    static final long MAX_TOTAL_BYTES = 2 * 1024 * 1024; // per analysis
    static final long MAX_ANALYSIS_MILLIS = 60_000;   // wall-clock budget

    private static final Set<String> EXCLUDED_DIR_SEGMENTS = Set.of(
        "node_modules", "dist", "build", "target", "vendor", ".git", "coverage",
        "out", ".next", ".venv", "venv", "__pycache__", "dev-dist");

    private static final Set<String> INTERESTING_FILE_NAMES = Set.of(
        "package.json", "pom.xml", "build.gradle", "build.gradle.kts",
        "requirements.txt", "pyproject.toml", "pipfile",
        "application.properties", "application.yml", "application.yaml",
        "dockerfile", "docker-compose.yml", "docker-compose.yaml",
        "netlify.toml", "vercel.json", "render.yaml", "procfile",
        ".gitignore",
        "firebase.json", "angular.json",
        ".env.example", ".env.sample", ".env.template", "env.example",
        // Common Node entry points, scanned for Express health routes
        "server.js", "server.ts", "app.js", "app.ts", "index.js", "index.ts",
        "main.js", "main.ts");

    private static final Set<String> INTERESTING_PREFIXES = Set.of("vite.config.", "next.config.");

    private final RepositoryFileReader fileReader;
    private final StackDetectionService stackDetectionService;
    private final RepositoryAnalysisRepository analysisRepository;
    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;

    public RepositoryAnalysisService(RepositoryFileReader fileReader,
                                     StackDetectionService stackDetectionService,
                                     RepositoryAnalysisRepository analysisRepository,
                                     ProjectRepository projectRepository,
                                     ObjectMapper objectMapper) {
        this.fileReader = fileReader;
        this.stackDetectionService = stackDetectionService;
        this.analysisRepository = analysisRepository;
        this.projectRepository = projectRepository;
        this.objectMapper = objectMapper;
    }

    // Deliberately not @Transactional: a failed analysis must still persist its
    // FAILED record, and the thrown RepositoryAccessException would roll it back.
    public RepositoryAnalysisResponse analyze(Long projectId, String repositoryInput) {
        Project project = requireOwnedProject(projectId);
        RepositoryRef ref = RepositoryRef.parse(repositoryInput);

        RepositoryAnalysis analysis = new RepositoryAnalysis();
        analysis.setProjectId(project.getId());
        analysis.setUserId(project.getUserId());
        analysis.setRepositoryFullName(ref.fullName());
        analysis.setStatus(AnalysisStatus.RUNNING);
        analysis = analysisRepository.save(analysis);

        try {
            StackDetectionResult result = runDetection(ref);
            analysis.setResultJson(objectMapper.writeValueAsString(result));
            analysis.setStatus(AnalysisStatus.COMPLETED);
            // Save repository metadata against the project
            project.setGithubUrl("https://github.com/" + ref.fullName());
            projectRepository.save(project);
        } catch (RepositoryAccessException e) {
            analysis.setStatus(AnalysisStatus.FAILED);
            analysis.setErrorMessage(truncate(e.getMessage(), 500));
            analysisRepository.save(analysis);
            throw e; // handled by GlobalExceptionHandler with an appropriate HTTP status
        } catch (Exception e) {
            log.error("Repository analysis failed for {}", ref.fullName(), e);
            analysis.setStatus(AnalysisStatus.FAILED);
            analysis.setErrorMessage("Analysis failed unexpectedly. Please try again.");
            analysisRepository.save(analysis);
            throw new RepositoryAccessException("Analysis failed unexpectedly. Please try again.", e);
        }
        return toResponse(analysisRepository.save(analysis));
    }

    /**
     * Runs detection without persisting anything — used by the import flow to
     * let the user review the detected stack before a project is created.
     */
    public StackDetectionResult detectOnly(String repositoryInput) {
        RepositoryRef ref = RepositoryRef.parse(repositoryInput);
        return runDetection(ref);
    }

    @Transactional(readOnly = true)
    public RepositoryAnalysisResponse getLatest(Long projectId) {
        requireOwnedProject(projectId);
        RepositoryAnalysis analysis = analysisRepository
            .findTopByProjectIdOrderByCreatedAtDesc(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("No analysis found for this project"));
        return toResponse(analysis);
    }

    // ---------- internals ----------

    private StackDetectionResult runDetection(RepositoryRef ref) {
        long deadline = System.currentTimeMillis() + MAX_ANALYSIS_MILLIS;
        List<String> warnings = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        RepositoryMetadata metadata = fileReader.fetchMetadata(ref);
        RepositoryFileReader.FileListing listing = fileReader.listFiles(ref, metadata.defaultBranch());
        if (listing.truncated()) {
            warnings.add("Repository file listing was truncated by the provider; "
                + "some files may not have been considered.");
        }
        if (listing.entries().size() > MAX_TREE_ENTRIES) {
            throw new RepositoryAccessException("Repository has more than " + MAX_TREE_ENTRIES
                + " files and is too large to analyse safely.");
        }

        List<String> allPaths = listing.entries().stream().map(RepositoryFileEntry::path).toList();
        Map<String, String> contents = new LinkedHashMap<>();
        long totalBytes = 0;

        for (RepositoryFileEntry entry : listing.entries()) {
            if (!isInteresting(entry.path())) continue;
            if (contents.size() >= MAX_FILES_TO_FETCH) {
                warnings.add("File download budget (" + MAX_FILES_TO_FETCH + ") reached; "
                    + entry.path() + " and later candidates were not analysed.");
                break;
            }
            if (System.currentTimeMillis() > deadline) {
                warnings.add("Analysis time budget reached; remaining files were not analysed.");
                break;
            }
            if (entry.size() > MAX_FILE_BYTES) {
                skipped.add(entry.path() + " (larger than " + (MAX_FILE_BYTES / 1024) + " KB)");
                continue;
            }
            if (totalBytes + Math.max(entry.size(), 0) > MAX_TOTAL_BYTES) {
                warnings.add("Total download budget reached; remaining files were not analysed.");
                break;
            }
            try {
                String content = fileReader.readTextFile(ref, metadata.defaultBranch(), entry.path(), MAX_FILE_BYTES);
                contents.put(entry.path(), content);
                totalBytes += content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            } catch (RepositoryAccessException.RateLimited | RepositoryAccessException.BadCredentials e) {
                throw e; // fatal for the whole analysis
            } catch (RepositoryAccessException e) {
                skipped.add(entry.path() + " (could not be read)");
            }
        }

        return stackDetectionService.detect(ref.fullName(), allPaths, contents, skipped, warnings);
    }

    /**
     * A file is worth downloading when it is a known configuration file, is not
     * inside a dependency/build/VCS directory, and is not a real secrets file.
     */
    boolean isInteresting(String path) {
        String[] segments = path.split("/");
        // deep paths are rarely app config; 6 still reaches maven's src/main/resources
        if (segments.length > 6) return false;
        for (int i = 0; i < segments.length - 1; i++) {
            if (EXCLUDED_DIR_SEGMENTS.contains(segments[i].toLowerCase(Locale.ROOT))) return false;
        }
        String fileName = segments[segments.length - 1];
        String lower = fileName.toLowerCase(Locale.ROOT);
        // never read real env files — only documented examples
        if (lower.equals(".env") || (lower.startsWith(".env.") && !isEnvExampleName(lower))) return false;
        if (INTERESTING_FILE_NAMES.contains(lower)) return true;
        return INTERESTING_PREFIXES.stream().anyMatch(lower::startsWith);
    }

    private boolean isEnvExampleName(String lower) {
        return lower.equals(".env.example") || lower.equals(".env.sample") || lower.equals(".env.template");
    }

    private Project requireOwnedProject(Long projectId) {
        Long userId = CurrentUserUtil.getCurrentUserId();
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        if (!project.getUserId().equals(userId)) {
            throw new UnauthorizedAccessException("Not your project");
        }
        return project;
    }

    private RepositoryAnalysisResponse toResponse(RepositoryAnalysis analysis) {
        RepositoryAnalysisResponse response = new RepositoryAnalysisResponse();
        response.setId(analysis.getId());
        response.setProjectId(analysis.getProjectId());
        response.setRepository(analysis.getRepositoryFullName());
        response.setStatus(analysis.getStatus());
        response.setErrorMessage(analysis.getErrorMessage());
        response.setCreatedAt(analysis.getCreatedAt());
        if (analysis.getResultJson() != null) {
            try {
                response.setResult(objectMapper.readValue(analysis.getResultJson(), StackDetectionResult.class));
            } catch (Exception e) {
                log.warn("Stored analysis {} has unreadable result JSON", analysis.getId());
            }
        }
        return response;
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}

package com.deploypilot.service;

import com.deploypilot.dto.BlueprintResponse;
import com.deploypilot.dto.BlueprintResult;
import com.deploypilot.dto.StackDetectionResult;
import com.deploypilot.exception.ResourceNotFoundException;
import com.deploypilot.exception.UnauthorizedAccessException;
import com.deploypilot.model.DeploymentBlueprint;
import com.deploypilot.model.Project;
import com.deploypilot.model.RepositoryAnalysis;
import com.deploypilot.model.enums.AnalysisStatus;
import com.deploypilot.repoaccess.RepositoryFileReader;
import com.deploypilot.repoaccess.RepositoryMetadata;
import com.deploypilot.repoaccess.RepositoryRef;
import com.deploypilot.repository.DeploymentBlueprintRepository;
import com.deploypilot.repository.ProjectRepository;
import com.deploypilot.repository.RepositoryAnalysisRepository;
import com.deploypilot.util.CurrentUserUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates blueprint generation: ownership checks, loading the latest
 * successful analysis, best-effort retrieval of existing config files for
 * diffs, override validation, persistence and staleness detection.
 */
@Service
public class DeploymentBlueprintService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentBlueprintService.class);
    private static final int MAX_PREVIEW_FILE_BYTES = 50 * 1024;

    private final BlueprintGenerationService generator;
    private final DeploymentBlueprintRepository blueprintRepository;
    private final RepositoryAnalysisRepository analysisRepository;
    private final ProjectRepository projectRepository;
    private final RepositoryFileReader fileReader;
    private final ObjectMapper objectMapper;

    public DeploymentBlueprintService(BlueprintGenerationService generator,
                                      DeploymentBlueprintRepository blueprintRepository,
                                      RepositoryAnalysisRepository analysisRepository,
                                      ProjectRepository projectRepository,
                                      RepositoryFileReader fileReader,
                                      ObjectMapper objectMapper) {
        this.generator = generator;
        this.blueprintRepository = blueprintRepository;
        this.analysisRepository = analysisRepository;
        this.projectRepository = projectRepository;
        this.fileReader = fileReader;
        this.objectMapper = objectMapper;
    }

    /** Generates and persists a fresh blueprint from the latest successful analysis. */
    public BlueprintResponse generate(Long projectId) {
        requireOwnedProject(projectId);
        RepositoryAnalysis analysis = latestCompletedAnalysis(projectId);
        StackDetectionResult detection = readDetection(analysis);

        Map<String, String> currentFiles = fetchExistingConfigFiles(analysis, detection);
        BlueprintResult result = generator.generate(detection, Map.of(), currentFiles);

        DeploymentBlueprint blueprint = new DeploymentBlueprint();
        blueprint.setProjectId(projectId);
        blueprint.setUserId(CurrentUserUtil.getCurrentUserId());
        blueprint.setAnalysisId(analysis.getId());
        blueprint.setRulesVersion(BlueprintGenerationService.RULES_VERSION);
        writeJson(blueprint, result, Map.of());
        return toResponse(blueprintRepository.save(blueprint), false);
    }

    @Transactional(readOnly = true)
    public BlueprintResponse getLatest(Long projectId) {
        requireOwnedProject(projectId);
        DeploymentBlueprint blueprint = blueprintRepository
            .findTopByProjectIdOrderByCreatedAtDesc(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("No blueprint found for this project"));
        return toResponse(blueprint, isStale(projectId, blueprint));
    }

    /**
     * Applies platform overrides and deterministically recalculates the
     * blueprint from the same analysis. The original recommendation is
     * preserved inside each component for comparison.
     */
    public BlueprintResponse applyOverrides(Long projectId, Map<String, String> overrides) {
        requireOwnedProject(projectId);
        DeploymentBlueprint blueprint = blueprintRepository
            .findTopByProjectIdOrderByCreatedAtDesc(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Generate a blueprint before overriding platforms"));
        RepositoryAnalysis analysis = analysisRepository.findById(blueprint.getAnalysisId())
            .orElseThrow(() -> new ResourceNotFoundException("The analysis behind this blueprint no longer exists"));
        StackDetectionResult detection = readDetection(analysis);

        Map<String, String> merged = new LinkedHashMap<>(readOverrides(blueprint));
        overrides.forEach((k, v) -> {
            if (v == null || v.isBlank()) merged.remove(k); else merged.put(k, v.trim());
        });
        validateOverrides(detection, merged);

        Map<String, String> currentFiles = fetchExistingConfigFiles(analysis, detection);
        BlueprintResult result = generator.generate(detection, merged, currentFiles);
        writeJson(blueprint, result, merged);
        return toResponse(blueprintRepository.save(blueprint), isStale(projectId, blueprint));
    }

    // ---------- internals ----------

    private void validateOverrides(StackDetectionResult detection, Map<String, String> overrides) {
        if (overrides.isEmpty()) return;
        BlueprintResult reference = generator.generate(detection, Map.of(), Map.of());
        Map<String, List<String>> allowedByComponent = new HashMap<>();
        for (BlueprintResult.Component c : reference.getComponents()) {
            allowedByComponent.put(c.getId(), generator.allowedPlatforms(c));
        }
        for (Map.Entry<String, String> e : overrides.entrySet()) {
            List<String> allowed = allowedByComponent.get(e.getKey());
            if (allowed == null) {
                throw new IllegalArgumentException("Unknown component: " + e.getKey());
            }
            if (!allowed.contains(e.getValue())) {
                throw new IllegalArgumentException(e.getValue() + " is not a compatible platform for "
                    + e.getKey() + ". Compatible options: " + String.join(", ", allowed));
            }
        }
    }

    private boolean isStale(Long projectId, DeploymentBlueprint blueprint) {
        return analysisRepository
            .findTopByProjectIdAndStatusOrderByCreatedAtDesc(projectId, AnalysisStatus.COMPLETED)
            .map(latest -> !latest.getId().equals(blueprint.getAnalysisId()))
            .orElse(false);
    }

    private RepositoryAnalysis latestCompletedAnalysis(Long projectId) {
        return analysisRepository
            .findTopByProjectIdAndStatusOrderByCreatedAtDesc(projectId, AnalysisStatus.COMPLETED)
            .orElseThrow(() -> new IllegalArgumentException(
                "Run a successful repository analysis before generating a blueprint"));
    }

    private StackDetectionResult readDetection(RepositoryAnalysis analysis) {
        try {
            return objectMapper.readValue(analysis.getResultJson(), StackDetectionResult.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("The stored analysis result is unreadable; re-run the analysis");
        }
    }

    /**
     * Best-effort read of existing deployment config files so previews can show
     * a diff. Never reads env files; failures degrade to previews without diffs.
     */
    private Map<String, String> fetchExistingConfigFiles(RepositoryAnalysis analysis, StackDetectionResult detection) {
        Map<String, String> contents = new LinkedHashMap<>();
        try {
            List<String> candidates = detection.getAnalyzedFiles().stream()
                .filter(p -> p.equals("netlify.toml") || p.equals("render.yaml")
                    || p.equals(".gitignore")
                    || p.equals("Dockerfile") || p.endsWith("/Dockerfile"))
                .toList();
            if (candidates.isEmpty()) return contents;
            RepositoryRef ref = RepositoryRef.parse(analysis.getRepositoryFullName());
            RepositoryMetadata metadata = fileReader.fetchMetadata(ref);
            for (String path : candidates) {
                try {
                    contents.put(path, fileReader.readTextFile(ref, metadata.defaultBranch(), path, MAX_PREVIEW_FILE_BYTES));
                } catch (Exception e) {
                    log.debug("Could not read {} for preview diff", path);
                }
            }
        } catch (Exception e) {
            log.debug("Config file retrieval for previews skipped: {}", e.getMessage());
        }
        return contents;
    }

    private void writeJson(DeploymentBlueprint blueprint, BlueprintResult result, Map<String, String> overrides) {
        try {
            blueprint.setBlueprintJson(objectMapper.writeValueAsString(result));
            blueprint.setOverridesJson(overrides.isEmpty() ? null : objectMapper.writeValueAsString(overrides));
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize blueprint", e);
        }
    }

    private Map<String, String> readOverrides(DeploymentBlueprint blueprint) {
        if (blueprint.getOverridesJson() == null) return Map.of();
        try {
            return objectMapper.readValue(blueprint.getOverridesJson(), new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private BlueprintResponse toResponse(DeploymentBlueprint blueprint, boolean stale) {
        BlueprintResponse r = new BlueprintResponse();
        r.setId(blueprint.getId());
        r.setProjectId(blueprint.getProjectId());
        r.setAnalysisId(blueprint.getAnalysisId());
        r.setRulesVersion(blueprint.getRulesVersion());
        r.setStale(stale);
        r.setOverrides(readOverrides(blueprint));
        r.setCreatedAt(blueprint.getCreatedAt());
        r.setUpdatedAt(blueprint.getUpdatedAt());
        try {
            r.setResult(objectMapper.readValue(blueprint.getBlueprintJson(), BlueprintResult.class));
        } catch (Exception e) {
            log.warn("Stored blueprint {} has unreadable JSON", blueprint.getId());
        }
        return r;
    }

    private void requireOwnedProject(Long projectId) {
        Long userId = CurrentUserUtil.getCurrentUserId();
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        if (!project.getUserId().equals(userId)) {
            throw new UnauthorizedAccessException("Not your project");
        }
    }
}

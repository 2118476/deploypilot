package com.deploypilot.service;

import com.deploypilot.dto.*;
import com.deploypilot.exception.ResourceNotFoundException;
import com.deploypilot.exception.UnauthorizedAccessException;
import com.deploypilot.model.*;
import com.deploypilot.model.enums.*;
import com.deploypilot.repository.*;
import com.deploypilot.util.CurrentUserUtil;
import com.deploypilot.util.DeploymentPlanGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectTechnologyRepository technologyRepository;
    private final ProjectServiceRepository serviceRepository;
    private final DeploymentPlanRepository deploymentPlanRepository;
    private final StepProgressRepository stepProgressRepository;
    private final ObjectMapper objectMapper;

    public ProjectService(ProjectRepository projectRepository, ProjectTechnologyRepository technologyRepository,
                          ProjectServiceRepository serviceRepository, DeploymentPlanRepository deploymentPlanRepository,
                          StepProgressRepository stepProgressRepository, ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.technologyRepository = technologyRepository;
        this.serviceRepository = serviceRepository;
        this.deploymentPlanRepository = deploymentPlanRepository;
        this.stepProgressRepository = stepProgressRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ProjectSummaryDto> getMyProjects() {
        Long userId = CurrentUserUtil.getCurrentUserId();
        return projectRepository.findByUserId(userId).stream()
                .map(this::toSummaryDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(Long id) {
        Project project = findAndVerifyOwnership(id);
        return toResponse(project);
    }

    @Transactional
    public ApiResponse<ProjectResponse> createProject(ProjectCreateRequest request) {
        Long userId = CurrentUserUtil.getCurrentUserId();
        Project project = new Project();
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setGithubUrl(request.getGithubUrl());
        project.setLocalFolderPath(request.getLocalFolderPath());
        project.setStatus(ProjectStatus.PLANNING);
        project.setUserId(userId);

        Project saved = projectRepository.save(project);
        return ApiResponse.ok("Project created", toResponse(saved));
    }

    @Transactional
    public ApiResponse<ProjectResponse> updateProject(Long id, ProjectCreateRequest request) {
        Project project = findAndVerifyOwnership(id);
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setGithubUrl(request.getGithubUrl());
        project.setLocalFolderPath(request.getLocalFolderPath());
        Project saved = projectRepository.save(project);
        return ApiResponse.ok("Project updated", toResponse(saved));
    }

    @Transactional
    public ApiResponse<Void> deleteProject(Long id) {
        findAndVerifyOwnership(id);
        projectRepository.deleteById(id);
        return ApiResponse.ok("Project deleted", null);
    }

    @Transactional
    public ApiResponse<DeploymentPlanResponse> generateDeploymentPlan(Long projectId, TechnologySelectionRequest request) {
        Project project = findAndVerifyOwnership(projectId);

        // Save technologies
        technologyRepository.deleteByProjectId(projectId);
        if (request.getProjectType() != null) {
            saveTech(projectId, TechnologyCategory.FRONTEND, request.getProjectType(), null);
        }
        if (request.getFrontendTech() != null && !"none".equals(request.getFrontendTech())) {
            saveTech(projectId, TechnologyCategory.FRONTEND, request.getFrontendTech(), null);
        }
        if (request.getBackendTech() != null && !"none".equals(request.getBackendTech())) {
            saveTech(projectId, TechnologyCategory.BACKEND, request.getBackendTech(), null);
        }
        if (request.getDatabase() != null && !"none".equals(request.getDatabase())) {
            saveTech(projectId, TechnologyCategory.DATABASE, request.getDatabase(), null);
        }
        if (request.getFrontendHost() != null) {
            saveTech(projectId, TechnologyCategory.HOSTING, request.getFrontendHost(), null);
        }
        if (request.getBackendHost() != null) {
            saveTech(projectId, TechnologyCategory.HOSTING, request.getBackendHost(), null);
        }
        if (request.getAdditionalServices() != null) {
            for (String svc : request.getAdditionalServices()) {
                TechnologyCategory cat = categorizeService(svc);
                saveTech(projectId, cat, svc, null);
            }
        }

        // Generate plan
        List<DeploymentStepDto> steps = DeploymentPlanGenerator.generatePlan(request);
        String planJson;
        try {
            planJson = objectMapper.writeValueAsString(steps);
        } catch (JsonProcessingException e) {
            return ApiResponse.error("Failed to generate deployment plan");
        }

        // Delete old plan
        deploymentPlanRepository.findByProjectId(projectId).ifPresent(deploymentPlanRepository::delete);

        DeploymentPlan plan = new DeploymentPlan();
        plan.setProjectId(projectId);
        plan.setGeneratedAt(Instant.now());
        plan.setPlanJson(planJson);
        plan.setCurrentStepIndex(0);
        plan.setStatus(DeploymentStatus.ACTIVE);
        deploymentPlanRepository.save(plan);

        project.setStatus(ProjectStatus.IN_PROGRESS);
        projectRepository.save(project);

        DeploymentPlanResponse response = new DeploymentPlanResponse();
        response.setProjectId(projectId);
        response.setSteps(steps);
        response.setCurrentStepIndex(0);
        response.setStatus(DeploymentStatus.ACTIVE);
        response.setTotalSteps(steps.size());
        response.setCompletedSteps(0);
        response.setGeneratedAt(Instant.now());

        return ApiResponse.ok("Deployment plan generated", response);
    }

    @Transactional(readOnly = true)
    public ApiResponse<DeploymentStepDto> getNextStep(Long projectId) {
        DeploymentPlan plan = deploymentPlanRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("No deployment plan for this project"));

        try {
            List<DeploymentStepDto> steps = objectMapper.readValue(plan.getPlanJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, DeploymentStepDto.class));
            if (plan.getCurrentStepIndex() < steps.size()) {
                return ApiResponse.ok(steps.get(plan.getCurrentStepIndex()));
            }
            return ApiResponse.ok("All steps completed", null);
        } catch (JsonProcessingException e) {
            return ApiResponse.error("Failed to read deployment plan");
        }
    }

    private void saveTech(Long projectId, TechnologyCategory category, String tech, String version) {
        ProjectTechnology pt = new ProjectTechnology();
        pt.setProjectId(projectId);
        pt.setCategory(category);
        pt.setTechnology(tech);
        pt.setVersion(version);
        technologyRepository.save(pt);
    }

    private TechnologyCategory categorizeService(String svc) {
        return switch (svc.toLowerCase()) {
            case "gemini", "openai" -> TechnologyCategory.AI;
            case "firebase-cloud-messaging" -> TechnologyCategory.NOTIFICATION;
            case "supabase-auth", "firebase-auth" -> TechnologyCategory.AUTH;
            case "file-storage" -> TechnologyCategory.STORAGE;
            case "email-service" -> TechnologyCategory.EMAIL;
            case "custom-domain" -> TechnologyCategory.DOMAIN;
            case "github-actions" -> TechnologyCategory.CI_CD;
            case "docker" -> TechnologyCategory.DOCKER;
            default -> TechnologyCategory.BACKEND;
        };
    }

    private Project findAndVerifyOwnership(Long id) {
        Long userId = CurrentUserUtil.getCurrentUserId();
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        if (!project.getUserId().equals(userId)) {
            throw new UnauthorizedAccessException("You do not own this project");
        }
        return project;
    }

    private ProjectResponse toResponse(Project p) {
        List<TechnologyDto> techs = technologyRepository.findByProjectId(p.getId()).stream()
                .map(t -> new TechnologyDto(t.getId(), t.getCategory(), t.getTechnology(), t.getVersion()))
                .collect(Collectors.toList());
        List<String> services = serviceRepository.findByProjectId(p.getId()).stream()
                .map(com.deploypilot.model.ProjectService::getServiceName)
                .collect(Collectors.toList());

        ProjectResponse r = new ProjectResponse(p.getId(), p.getName(), p.getDescription(),
                p.getGithubUrl(), p.getLocalFolderPath(), p.getStatus(), p.getUserId(),
                p.getCreatedAt(), p.getUpdatedAt());
        r.setTechnologies(techs);
        r.setServices(services);
        return r;
    }

    private ProjectSummaryDto toSummaryDto(Project p) {
        ProjectSummaryDto dto = new ProjectSummaryDto();
        dto.setId(p.getId());
        dto.setName(p.getName());
        dto.setDescription(p.getDescription());
        dto.setStatus(p.getStatus());

        List<ProjectTechnology> techs = technologyRepository.findByProjectId(p.getId());
        dto.setTechSummary(techs.stream().map(ProjectTechnology::getTechnology).collect(Collectors.joining(", ")));

        deploymentPlanRepository.findByProjectId(p.getId()).ifPresent(plan -> {
            dto.setCurrentStep(plan.getCurrentStepIndex() + 1);
            try {
                List<DeploymentStepDto> steps = objectMapper.readValue(plan.getPlanJson(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, DeploymentStepDto.class));
                dto.setTotalSteps(steps.size());
                long completed = steps.stream().filter(s -> s.getStatus() == StepStatus.COMPLETED).count();
                dto.setCompletedSteps((int) completed);
                if (plan.getCurrentStepIndex() < steps.size()) {
                    dto.setNextAction(steps.get(plan.getCurrentStepIndex()).getTitle());
                }
            } catch (Exception ignored) {}
        });

        dto.setCreatedAt(p.getCreatedAt());
        return dto;
    }
}

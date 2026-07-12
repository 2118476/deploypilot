package com.deploypilot.service;

import com.deploypilot.dto.ApiResponse;
import com.deploypilot.dto.DeploymentPlanResponse;
import com.deploypilot.dto.DeploymentStepDto;
import com.deploypilot.dto.StepProgressRequest;
import com.deploypilot.exception.ResourceNotFoundException;
import com.deploypilot.exception.UnauthorizedAccessException;
import com.deploypilot.model.*;
import com.deploypilot.model.enums.*;
import com.deploypilot.repository.*;
import com.deploypilot.util.CurrentUserUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class DeploymentPlanService {

    private final DeploymentPlanRepository planRepository;
    private final StepProgressRepository progressRepository;
    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;

    public DeploymentPlanService(DeploymentPlanRepository planRepository, StepProgressRepository progressRepository,
                                 ProjectRepository projectRepository, ObjectMapper objectMapper) {
        this.planRepository = planRepository;
        this.progressRepository = progressRepository;
        this.projectRepository = projectRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ApiResponse<DeploymentPlanResponse> getPlan(Long projectId) {
        verifyProjectOwnership(projectId);
        DeploymentPlan plan = planRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("No deployment plan found"));
        return ApiResponse.ok(toResponse(plan));
    }

    @Transactional
    public ApiResponse<DeploymentStepDto> updateStepProgress(Long projectId, int stepIndex, StepProgressRequest request) {
        verifyProjectOwnership(projectId);
        DeploymentPlan plan = planRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("No deployment plan found"));

        StepProgress progress = progressRepository.findByDeploymentPlanIdAndStepIndex(plan.getId(), stepIndex)
                .orElse(new StepProgress());
        progress.setDeploymentPlanId(plan.getId());
        progress.setStepIndex(stepIndex);
        progress.setStatus(request.getStatus());
        progress.setNote(request.getNote());
        if (request.getStatus() == StepStatus.COMPLETED) {
            progress.setCompletedAt(Instant.now());
            if (stepIndex == plan.getCurrentStepIndex()) {
                plan.setCurrentStepIndex(stepIndex + 1);
            }
        }
        progressRepository.save(progress);
        planRepository.save(plan);

        return getStep(plan, stepIndex);
    }

    @Transactional(readOnly = true)
    public ApiResponse<DeploymentStepDto> getCurrentStep(Long projectId) {
        verifyProjectOwnership(projectId);
        DeploymentPlan plan = planRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("No deployment plan found"));
        return getStep(plan, plan.getCurrentStepIndex());
    }

    private ApiResponse<DeploymentStepDto> getStep(DeploymentPlan plan, int stepIndex) {
        try {
            List<DeploymentStepDto> steps = objectMapper.readValue(plan.getPlanJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, DeploymentStepDto.class));
            if (stepIndex < steps.size()) {
                return ApiResponse.ok(steps.get(stepIndex));
            }
            return ApiResponse.ok("All steps completed", null);
        } catch (JsonProcessingException e) {
            return ApiResponse.error("Failed to read plan");
        }
    }

    private DeploymentPlanResponse toResponse(DeploymentPlan plan) {
        DeploymentPlanResponse r = new DeploymentPlanResponse();
        r.setId(plan.getId());
        r.setProjectId(plan.getProjectId());
        r.setCurrentStepIndex(plan.getCurrentStepIndex());
        r.setStatus(plan.getStatus());
        r.setGeneratedAt(plan.getGeneratedAt());
        r.setCreatedAt(plan.getCreatedAt());
        try {
            List<DeploymentStepDto> steps = objectMapper.readValue(plan.getPlanJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, DeploymentStepDto.class));
            // Merge progress
            List<StepProgress> progressList = progressRepository.findByDeploymentPlanId(plan.getId());
            for (StepProgress sp : progressList) {
                if (sp.getStepIndex() < steps.size()) {
                    steps.get(sp.getStepIndex()).setStatus(sp.getStatus());
                    steps.get(sp.getStepIndex()).setPersonalNote(sp.getNote());
                    steps.get(sp.getStepIndex()).setCompletedAt(sp.getCompletedAt());
                }
            }
            long completed = steps.stream().filter(s -> s.getStatus() == StepStatus.COMPLETED).count();
            r.setSteps(steps);
            r.setTotalSteps(steps.size());
            r.setCompletedSteps((int) completed);
        } catch (JsonProcessingException ignored) {}
        return r;
    }

    private void verifyProjectOwnership(Long projectId) {
        Long userId = CurrentUserUtil.getCurrentUserId();
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        if (!project.getUserId().equals(userId)) {
            throw new UnauthorizedAccessException("Not your project");
        }
    }
}

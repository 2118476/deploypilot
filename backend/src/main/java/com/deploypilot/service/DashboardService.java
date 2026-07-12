package com.deploypilot.service;

import com.deploypilot.dto.DashboardResponse;
import com.deploypilot.dto.ProjectSummaryDto;
import com.deploypilot.model.DeploymentPlan;
import com.deploypilot.model.Project;
import com.deploypilot.model.ProjectTechnology;
import com.deploypilot.model.enums.StepStatus;
import com.deploypilot.repository.*;
import com.deploypilot.util.CurrentUserUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.deploypilot.dto.DeploymentStepDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final ProjectRepository projectRepository;
    private final ProjectTechnologyRepository technologyRepository;
    private final DeploymentPlanRepository deploymentPlanRepository;
    private final BookmarkRepository bookmarkRepository;
    private final ObjectMapper objectMapper;

    public DashboardService(ProjectRepository projectRepository, ProjectTechnologyRepository technologyRepository,
                            DeploymentPlanRepository deploymentPlanRepository, BookmarkRepository bookmarkRepository,
                            ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.technologyRepository = technologyRepository;
        this.deploymentPlanRepository = deploymentPlanRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        Long userId = CurrentUserUtil.getCurrentUserId();
        List<Project> projects = projectRepository.findByUserId(userId);

        DashboardResponse dash = new DashboardResponse();
        dash.setTotalProjects(projects.size());
        dash.setBookmarkCount((int) bookmarkRepository.countByUserId(userId));

        List<ProjectSummaryDto> summaries = projects.stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
        dash.setProjects(summaries);

        int totalSteps = 0;
        int completedSteps = 0;
        String nextTitle = null;
        String nextAction = null;

        for (Project p : projects) {
            var planOpt = deploymentPlanRepository.findByProjectId(p.getId());
            if (planOpt.isPresent()) {
                DeploymentPlan plan = planOpt.get();
                try {
                    List<DeploymentStepDto> steps = objectMapper.readValue(plan.getPlanJson(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, DeploymentStepDto.class));
                    totalSteps += steps.size();
                    for (DeploymentStepDto s : steps) {
                        if (s.getStatus() == StepStatus.COMPLETED) completedSteps++;
                    }
                    if (plan.getCurrentStepIndex() < steps.size() && nextTitle == null) {
                        nextTitle = steps.get(plan.getCurrentStepIndex()).getTitle();
                        nextAction = steps.get(plan.getCurrentStepIndex()).getWhatToDo();
                    }
                } catch (JsonProcessingException ignored) {}
            }
        }

        dash.setTotalSteps(totalSteps);
        dash.setCompletedSteps(completedSteps);
        dash.setNextStepTitle(nextTitle != null ? nextTitle : "Create a project to get started");
        dash.setNextStepAction(nextAction != null ? nextAction : "Click 'New Project' to begin");

        return dash;
    }

    private ProjectSummaryDto toSummary(Project p) {
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
            } catch (Exception ignored) {}
        });

        dto.setCreatedAt(p.getCreatedAt());
        return dto;
    }
}

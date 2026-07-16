package com.deploypilot.service;

import com.deploypilot.dto.DeploymentTargetRequest;
import com.deploypilot.dto.DeploymentTargetResponse;
import com.deploypilot.exception.ResourceNotFoundException;
import com.deploypilot.exception.UnauthorizedAccessException;
import com.deploypilot.model.DeploymentTarget;
import com.deploypilot.model.Project;
import com.deploypilot.model.enums.DeploymentTargetType;
import com.deploypilot.repository.DeploymentTargetRepository;
import com.deploypilot.repository.ProjectRepository;
import com.deploypilot.util.CurrentUserUtil;
import com.deploypilot.verify.SafeUrlValidator;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class DeploymentTargetService {

    private final DeploymentTargetRepository targetRepository;
    private final ProjectRepository projectRepository;
    private final SafeUrlValidator urlValidator;

    public DeploymentTargetService(DeploymentTargetRepository targetRepository,
                                   ProjectRepository projectRepository,
                                   SafeUrlValidator urlValidator) {
        this.targetRepository = targetRepository;
        this.projectRepository = projectRepository;
        this.urlValidator = urlValidator;
    }

    public List<DeploymentTargetResponse> list(Long projectId) {
        requireOwnedProject(projectId);
        return targetRepository.findByProjectIdOrderByCreatedAtAsc(projectId)
            .stream().map(DeploymentTargetResponse::from).toList();
    }

    public DeploymentTargetResponse create(Long projectId, DeploymentTargetRequest request) {
        Project project = requireOwnedProject(projectId);
        DeploymentTarget target = new DeploymentTarget();
        target.setProjectId(projectId);
        target.setUserId(project.getUserId());
        apply(target, request);
        return DeploymentTargetResponse.from(targetRepository.save(target));
    }

    public DeploymentTargetResponse update(Long projectId, Long targetId, DeploymentTargetRequest request) {
        requireOwnedProject(projectId);
        DeploymentTarget target = ownedTarget(projectId, targetId);
        apply(target, request);
        return DeploymentTargetResponse.from(targetRepository.save(target));
    }

    public void delete(Long projectId, Long targetId) {
        requireOwnedProject(projectId);
        targetRepository.delete(ownedTarget(projectId, targetId));
    }

    private void apply(DeploymentTarget target, DeploymentTargetRequest request) {
        DeploymentTargetType type;
        try {
            type = DeploymentTargetType.valueOf(request.getTargetType().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("Target type must be one of FRONTEND, BACKEND, HEALTH, VERSION");
        }
        // production targets must be safe HTTPS URLs (SSRF guard)
        urlValidator.validateProduction(request.getUrl().trim());
        target.setTargetType(type);
        target.setUrl(request.getUrl().trim());
        target.setComponentId(request.getComponentId());
        target.setPlatform(request.getPlatform());
        target.setHealthPath(request.getHealthPath());
        target.setExpectedCommit(request.getExpectedCommit());
    }

    private DeploymentTarget ownedTarget(Long projectId, Long targetId) {
        return targetRepository.findById(targetId)
            .filter(t -> t.getProjectId().equals(projectId))
            .orElseThrow(() -> new ResourceNotFoundException("Deployment target not found"));
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
}

package com.deploypilot.controller;

import com.deploypilot.dto.*;
import com.deploypilot.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) { this.projectService = projectService; }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectSummaryDto>>> getMyProjects() {
        return ResponseEntity.ok(ApiResponse.ok(projectService.getMyProjects()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProject(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.getProject(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(@Valid @RequestBody ProjectCreateRequest request) {
        return ResponseEntity.ok(projectService.createProject(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(@PathVariable Long id, @Valid @RequestBody ProjectCreateRequest request) {
        return ResponseEntity.ok(projectService.updateProject(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProject(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.deleteProject(id));
    }

    @PostMapping("/{id}/generate-plan")
    public ResponseEntity<ApiResponse<DeploymentPlanResponse>> generatePlan(@PathVariable Long id, @RequestBody TechnologySelectionRequest request) {
        return ResponseEntity.ok(projectService.generateDeploymentPlan(id, request));
    }

    @GetMapping("/{id}/next-step")
    public ResponseEntity<ApiResponse<DeploymentStepDto>> getNextStep(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getNextStep(id));
    }
}

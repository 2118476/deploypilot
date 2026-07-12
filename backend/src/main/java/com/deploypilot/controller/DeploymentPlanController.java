package com.deploypilot.controller;

import com.deploypilot.dto.*;
import com.deploypilot.service.DeploymentPlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/projects/{projectId}")
public class DeploymentPlanController {

    private final DeploymentPlanService planService;

    public DeploymentPlanController(DeploymentPlanService planService) { this.planService = planService; }

    @GetMapping("/plan")
    public ResponseEntity<ApiResponse<DeploymentPlanResponse>> getPlan(@PathVariable Long projectId) {
        return ResponseEntity.ok(planService.getPlan(projectId));
    }

    @PostMapping("/steps/{stepIndex}/progress")
    public ResponseEntity<ApiResponse<DeploymentStepDto>> updateStep(@PathVariable Long projectId, @PathVariable int stepIndex,
                                                                      @RequestBody StepProgressRequest request) {
        return ResponseEntity.ok(planService.updateStepProgress(projectId, stepIndex, request));
    }

    @GetMapping("/steps/current")
    public ResponseEntity<ApiResponse<DeploymentStepDto>> getCurrentStep(@PathVariable Long projectId) {
        return ResponseEntity.ok(planService.getCurrentStep(projectId));
    }
}

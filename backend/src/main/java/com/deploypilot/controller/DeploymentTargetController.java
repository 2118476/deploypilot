package com.deploypilot.controller;

import com.deploypilot.dto.ApiResponse;
import com.deploypilot.dto.DeploymentTargetRequest;
import com.deploypilot.dto.DeploymentTargetResponse;
import com.deploypilot.service.DeploymentTargetService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/projects/{projectId}/targets")
public class DeploymentTargetController {

    private final DeploymentTargetService targetService;

    public DeploymentTargetController(DeploymentTargetService targetService) {
        this.targetService = targetService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DeploymentTargetResponse>>> list(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.ok(targetService.list(projectId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DeploymentTargetResponse>> create(
            @PathVariable Long projectId, @Valid @RequestBody DeploymentTargetRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Target added", targetService.create(projectId, request)));
    }

    @PutMapping("/{targetId}")
    public ResponseEntity<ApiResponse<DeploymentTargetResponse>> update(
            @PathVariable Long projectId, @PathVariable Long targetId,
            @Valid @RequestBody DeploymentTargetRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Target updated", targetService.update(projectId, targetId, request)));
    }

    @DeleteMapping("/{targetId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long projectId, @PathVariable Long targetId) {
        targetService.delete(projectId, targetId);
        return ResponseEntity.ok(ApiResponse.ok("Target removed", null));
    }
}

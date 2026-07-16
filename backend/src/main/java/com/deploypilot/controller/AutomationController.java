package com.deploypilot.controller;

import com.deploypilot.dto.*;
import com.deploypilot.service.AutomationService;
import com.deploypilot.service.SecretService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlled deployment automation. The plan endpoint is read-only; nothing
 * external changes until a run is confirmed and executed. Ownership is enforced
 * in the service on every call.
 */
@RestController
@RequestMapping("/projects/{projectId}/automation")
public class AutomationController {

    private final AutomationService automationService;
    private final SecretService secretService;

    public AutomationController(AutomationService automationService, SecretService secretService) {
        this.automationService = automationService;
        this.secretService = secretService;
    }

    /** Generate (never execute) an action plan for review. */
    @PostMapping("/plan")
    public ResponseEntity<ApiResponse<DeploymentActionPlan>> plan(
            @PathVariable Long projectId, @Valid @RequestBody(required = false) PlanRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
            automationService.plan(projectId, request == null ? new PlanRequest() : request)));
    }

    /** Issue a short-lived confirmation for the reviewed plan. No external change yet. */
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<ConfirmationResponse>> confirm(
            @PathVariable Long projectId, @Valid @RequestBody ConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Confirmation issued",
            automationService.confirm(projectId, request)));
    }

    /** Execute a confirmed run. External changes begin here. */
    @PostMapping("/runs/{runId}/execute")
    public ResponseEntity<ApiResponse<AutomationRunResponse>> execute(
            @PathVariable Long projectId, @PathVariable Long runId, @Valid @RequestBody ExecuteRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Execution started",
            automationService.execute(projectId, runId, request)));
    }

    /** Retry a failed or paused run from the failed step (requires a fresh confirmation). */
    @PostMapping("/runs/{runId}/retry")
    public ResponseEntity<ApiResponse<AutomationRunResponse>> retry(
            @PathVariable Long projectId, @PathVariable Long runId, @Valid @RequestBody ExecuteRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Retry started",
            automationService.retry(projectId, runId, request)));
    }

    @GetMapping("/runs")
    public ResponseEntity<ApiResponse<List<AutomationRunResponse>>> runs(
            @PathVariable Long projectId, @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(automationService.list(projectId, limit)));
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<ApiResponse<AutomationRunResponse>> run(
            @PathVariable Long projectId, @PathVariable Long runId) {
        return ResponseEntity.ok(ApiResponse.ok(automationService.getRun(projectId, runId)));
    }

    // ---------- user-supplied deployment secrets (write-only values) ----------

    @GetMapping("/secrets")
    public ResponseEntity<ApiResponse<List<SecretView>>> secrets(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.ok(secretService.list(projectId)));
    }

    @PutMapping("/secrets")
    public ResponseEntity<ApiResponse<SecretView>> saveSecret(
            @PathVariable Long projectId, @Valid @RequestBody SecretRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Saved",
            secretService.save(projectId, request.getName(), request.getValue(), request.getDestination())));
    }

    @DeleteMapping("/secrets/{name}")
    public ResponseEntity<ApiResponse<Void>> removeSecret(
            @PathVariable Long projectId, @PathVariable String name) {
        secretService.remove(projectId, name);
        return ResponseEntity.ok(ApiResponse.ok("Removed", null));
    }
}

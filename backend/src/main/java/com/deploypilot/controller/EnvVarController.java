package com.deploypilot.controller;

import com.deploypilot.dto.*;
import com.deploypilot.service.EnvVarService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/projects/{projectId}/env-vars")
public class EnvVarController {

    private final EnvVarService envVarService;

    public EnvVarController(EnvVarService envVarService) { this.envVarService = envVarService; }

    @GetMapping
    public ResponseEntity<ApiResponse<List<EnvVarResponse>>> getEnvVars(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.ok(envVarService.getProjectEnvVars(projectId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EnvVarResponse>> createEnvVar(@PathVariable Long projectId, @RequestBody EnvVarCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Created", envVarService.createProjectEnvVar(projectId, request)));
    }

    @PutMapping("/{varId}")
    public ResponseEntity<ApiResponse<EnvVarResponse>> updateEnvVar(@PathVariable Long projectId, @PathVariable Long varId, @RequestBody EnvVarUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Updated", envVarService.updateEnvVar(varId, request)));
    }

    @DeleteMapping("/{varId}")
    public ResponseEntity<ApiResponse<Void>> deleteEnvVar(@PathVariable Long projectId, @PathVariable Long varId) {
        envVarService.deleteEnvVar(varId);
        return ResponseEntity.ok(ApiResponse.ok("Deleted", null));
    }
}

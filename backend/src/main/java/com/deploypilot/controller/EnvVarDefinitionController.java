package com.deploypilot.controller;

import com.deploypilot.dto.ApiResponse;
import com.deploypilot.dto.EnvVarDefinitionResponse;
import com.deploypilot.service.EnvVarService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/env-var-definitions")
public class EnvVarDefinitionController {

    private final EnvVarService envVarService;

    public EnvVarDefinitionController(EnvVarService envVarService) { this.envVarService = envVarService; }

    @GetMapping
    public ResponseEntity<ApiResponse<List<EnvVarDefinitionResponse>>> getDefinitions() {
        return ResponseEntity.ok(ApiResponse.ok(envVarService.getDefinitions()));
    }
}

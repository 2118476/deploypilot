package com.deploypilot.controller;

import com.deploypilot.dto.AnalyzeRepositoryRequest;
import com.deploypilot.dto.ApiResponse;
import com.deploypilot.dto.RepositoryAnalysisResponse;
import com.deploypilot.service.RepositoryAnalysisService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/projects/{projectId}/analysis")
public class RepositoryAnalysisController {

    private final RepositoryAnalysisService analysisService;

    public RepositoryAnalysisController(RepositoryAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /** Runs a read-only analysis of the given GitHub repository. */
    @PostMapping
    public ResponseEntity<ApiResponse<RepositoryAnalysisResponse>> analyze(
            @PathVariable Long projectId,
            @Valid @RequestBody AnalyzeRepositoryRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Analysis completed",
            analysisService.analyze(projectId, request.getRepository())));
    }

    /** Returns the most recent analysis for the project. */
    @GetMapping
    public ResponseEntity<ApiResponse<RepositoryAnalysisResponse>> latest(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.ok(analysisService.getLatest(projectId)));
    }
}

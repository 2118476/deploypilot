package com.deploypilot.controller;

import com.deploypilot.dto.AnalyzeRepositoryRequest;
import com.deploypilot.dto.ApiResponse;
import com.deploypilot.dto.ImportRepositoryResponse;
import com.deploypilot.dto.StackDetectionResult;
import com.deploypilot.service.ProjectImportService;
import com.deploypilot.service.RepositoryAnalysisService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RepositoryImportController {

    private final RepositoryAnalysisService analysisService;
    private final ProjectImportService importService;

    public RepositoryImportController(RepositoryAnalysisService analysisService,
                                      ProjectImportService importService) {
        this.analysisService = analysisService;
        this.importService = importService;
    }

    /**
     * Read-only detection preview for the import flow — nothing is persisted,
     * so the user can review the detected stack before a project is created.
     */
    @PostMapping("/repositories/preview-analysis")
    public ResponseEntity<ApiResponse<StackDetectionResult>> preview(
            @Valid @RequestBody AnalyzeRepositoryRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(analysisService.detectOnly(request.getRepository())));
    }

    /** Creates a project from a GitHub repository: analysis + blueprint in one step. */
    @PostMapping("/projects/import")
    public ResponseEntity<ApiResponse<ImportRepositoryResponse>> importRepository(
            @Valid @RequestBody AnalyzeRepositoryRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Project imported",
            importService.importRepository(request.getRepository())));
    }
}

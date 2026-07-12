package com.deploypilot.controller;

import com.deploypilot.dto.*;
import com.deploypilot.service.ErrorReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/troubleshoot")
public class ErrorReportController {

    private final ErrorReportService errorReportService;

    public ErrorReportController(ErrorReportService errorReportService) { this.errorReportService = errorReportService; }

    @PostMapping
    public ResponseEntity<ApiResponse<ErrorReportResponse>> submitError(@RequestBody ErrorReportRequest request) {
        return ResponseEntity.ok(errorReportService.submitError(request));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ErrorReportResponse>>> getHistory() {
        return ResponseEntity.ok(ApiResponse.ok(errorReportService.getHistory()));
    }
}

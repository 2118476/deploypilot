package com.deploypilot.service;

import com.deploypilot.dto.*;
import com.deploypilot.model.ErrorReport;
import com.deploypilot.repository.ErrorReportRepository;
import com.deploypilot.util.CurrentUserUtil;
import com.deploypilot.util.SecretRedactionUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ErrorReportService {

    private final ErrorReportRepository repo;
    private final GeminiService geminiService;

    public ErrorReportService(ErrorReportRepository repo, GeminiService geminiService) {
        this.repo = repo; this.geminiService = geminiService;
    }

    @Transactional
    public ApiResponse<ErrorReportResponse> submitError(ErrorReportRequest request) {
        Long userId = CurrentUserUtil.getCurrentUserId();
        String redacted = SecretRedactionUtil.redact(request.getContent());

        String aiResponse = geminiService.troubleshoot(redacted);

        ErrorReport report = new ErrorReport();
        report.setUserId(userId);
        report.setProjectId(request.getProjectId());
        report.setErrorType(request.getErrorType());
        report.setOriginalContent("[REDACTED]");
        report.setRedactedContent(redacted);
        report.setAiResponse(aiResponse);
        report.setResolved(false);

        ErrorReport saved = repo.save(report);

        ErrorReportResponse r = new ErrorReportResponse();
        r.setId(saved.getId());
        r.setErrorType(saved.getErrorType());
        r.setRedactedContent(saved.getRedactedContent());
        r.setAiResponse(saved.getAiResponse());
        r.setResolved(saved.isResolved());
        r.setCreatedAt(saved.getCreatedAt());

        return ApiResponse.ok("Analysis complete", r);
    }

    @Transactional(readOnly = true)
    public List<ErrorReportResponse> getHistory() {
        Long userId = CurrentUserUtil.getCurrentUserId();
        return repo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(r -> {
                    ErrorReportResponse dto = new ErrorReportResponse();
                    dto.setId(r.getId()); dto.setErrorType(r.getErrorType());
                    dto.setRedactedContent(r.getRedactedContent());
                    dto.setAiResponse(r.getAiResponse());
                    dto.setResolved(r.isResolved()); dto.setCreatedAt(r.getCreatedAt());
                    return dto;
                }).collect(Collectors.toList());
    }
}

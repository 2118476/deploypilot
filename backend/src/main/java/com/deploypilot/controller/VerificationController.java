package com.deploypilot.controller;

import com.deploypilot.dto.ApiResponse;
import com.deploypilot.dto.StartVerificationRequest;
import com.deploypilot.dto.VerificationRunResponse;
import com.deploypilot.service.DeploymentVerificationService;
import com.deploypilot.service.ProjectAssistService;
import com.deploypilot.verify.LogSanitizer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/projects/{projectId}")
public class VerificationController {

    private final DeploymentVerificationService verificationService;
    private final ProjectAssistService assistService;
    private final LogSanitizer logSanitizer;

    public VerificationController(DeploymentVerificationService verificationService,
                                  ProjectAssistService assistService,
                                  LogSanitizer logSanitizer) {
        this.verificationService = verificationService;
        this.assistService = assistService;
        this.logSanitizer = logSanitizer;
    }

    /** Starts an asynchronous verification run; poll the returned run id for the result. */
    @PostMapping("/verifications")
    public ResponseEntity<ApiResponse<VerificationRunResponse>> start(
            @PathVariable Long projectId, @Valid @RequestBody StartVerificationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Verification started",
            verificationService.start(projectId, request)));
    }

    @GetMapping("/verifications")
    public ResponseEntity<ApiResponse<List<VerificationRunResponse>>> list(
            @PathVariable Long projectId, @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(verificationService.list(projectId, limit)));
    }

    @GetMapping("/verifications/{runId}")
    public ResponseEntity<ApiResponse<VerificationRunResponse>> get(
            @PathVariable Long projectId, @PathVariable Long runId) {
        return ResponseEntity.ok(ApiResponse.ok(verificationService.getRun(projectId, runId)));
    }

    public static class LogRequest {
        @NotBlank(message = "Log content is required")
        @Size(max = LogSanitizer.MAX_CHARS + 10_000, message = "Log is too large")
        private String content;
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    /**
     * Sanitises a pasted deployment log and returns the preview. The original
     * unsanitised content is never persisted. Ownership enforced in the service.
     */
    @PostMapping("/logs/sanitize")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sanitizeLog(
            @PathVariable Long projectId, @Valid @RequestBody LogRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(assistService.sanitizeLog(projectId, request.getContent())));
    }

    public static class AssistRequest {
        @Size(max = 2_000) private String question;
        @Size(max = LogSanitizer.MAX_CHARS + 10_000) private String log;
        public String getQuestion() { return question; } public void setQuestion(String q) { this.question = q; }
        public String getLog() { return log; } public void setLog(String l) { this.log = l; }
    }

    /** Project-aware troubleshooting: sanitised context + optional log, optionally explained by AI. */
    @PostMapping("/assist")
    public ResponseEntity<ApiResponse<Map<String, Object>>> assist(
            @PathVariable Long projectId, @Valid @RequestBody AssistRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
            assistService.assist(projectId, request.getQuestion(), request.getLog())));
    }
}

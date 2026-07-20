package com.deploypilot.controller;

import com.deploypilot.dto.ApiResponse;
import com.deploypilot.dto.ConversationResponse;
import com.deploypilot.dto.CopilotMessageRequest;
import com.deploypilot.dto.CopilotMessageResponse;
import com.deploypilot.dto.TroubleshootRequest;
import com.deploypilot.service.CopilotService;
import com.deploypilot.troubleshoot.StructuredTroubleshooting;
import com.deploypilot.troubleshoot.TroubleshootingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Persistent, project-aware Copilot. Every operation is bound to the current user
 * and project; ownership is enforced in the service. The Copilot performs only
 * read-only operations and prepares plans — it never executes anything.
 */
@RestController
@RequestMapping("/projects/{projectId}/copilot")
public class CopilotController {

    private final CopilotService copilotService;
    private final TroubleshootingService troubleshootingService;

    public CopilotController(CopilotService copilotService, TroubleshootingService troubleshootingService) {
        this.copilotService = copilotService;
        this.troubleshootingService = troubleshootingService;
    }

    @GetMapping("/conversations/current")
    public ResponseEntity<ApiResponse<ConversationResponse>> current(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.ok(copilotService.getCurrentConversation(projectId)));
    }

    @PostMapping("/messages")
    public ResponseEntity<ApiResponse<CopilotMessageResponse>> send(
            @PathVariable Long projectId, @Valid @RequestBody CopilotMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(copilotService.sendMessage(projectId, request.getMessage())));
    }

    @DeleteMapping("/conversations/current")
    public ResponseEntity<ApiResponse<Void>> clear(@PathVariable Long projectId) {
        copilotService.clearConversation(projectId);
        return ResponseEntity.ok(ApiResponse.ok("Conversation cleared", null));
    }

    /**
     * Evidence-driven troubleshooting for a failed deployment step. Returns a
     * validated structured diagnosis (deterministic ground truth, optionally with a
     * Gemini explanation). Never executes or authorises anything.
     */
    @PostMapping("/troubleshoot")
    public ResponseEntity<ApiResponse<StructuredTroubleshooting>> troubleshoot(
            @PathVariable Long projectId, @RequestBody(required = false) TroubleshootRequest request) {
        TroubleshootRequest r = request != null ? request : new TroubleshootRequest();
        return ResponseEntity.ok(ApiResponse.ok(
            troubleshootingService.troubleshoot(projectId, r.getRunId(), r.getStepId(), r.getQuestion())));
    }

    /**
     * Records a user-reported safe troubleshooting event (e.g. MANUAL_DEPLOY_SUCCEEDED)
     * and returns the updated diagnosis. Prevents repetitive loops and drives the
     * host-key Case A / Case B branching. Never accepts secret values.
     */
    @PostMapping("/troubleshoot/event")
    public ResponseEntity<ApiResponse<StructuredTroubleshooting>> troubleshootEvent(
            @PathVariable Long projectId, @RequestBody TroubleshootRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
            troubleshootingService.recordUserEvent(projectId, request.getRunId(), request.getEvent())));
    }
}

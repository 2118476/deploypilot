package com.deploypilot.controller;

import com.deploypilot.dto.ApiResponse;
import com.deploypilot.dto.ConversationResponse;
import com.deploypilot.dto.CopilotMessageRequest;
import com.deploypilot.dto.CopilotMessageResponse;
import com.deploypilot.service.CopilotService;
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

    public CopilotController(CopilotService copilotService) {
        this.copilotService = copilotService;
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
}

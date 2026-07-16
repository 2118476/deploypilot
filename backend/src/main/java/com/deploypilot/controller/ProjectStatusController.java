package com.deploypilot.controller;

import com.deploypilot.dto.ActivityEventResponse;
import com.deploypilot.dto.ApiResponse;
import com.deploypilot.dto.ProjectStatusResponse;
import com.deploypilot.service.ProjectStatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Deterministic project status and activity timeline. Both are computed from
 * stored records and work with or without AI. Ownership is enforced in the service.
 */
@RestController
@RequestMapping("/projects/{projectId}")
public class ProjectStatusController {

    private final ProjectStatusService statusService;

    public ProjectStatusController(ProjectStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<ProjectStatusResponse>> status(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.ok(statusService.getStatus(projectId)));
    }

    @GetMapping("/activity")
    public ResponseEntity<ApiResponse<List<ActivityEventResponse>>> activity(
            @PathVariable Long projectId, @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(statusService.getActivity(projectId, limit)));
    }
}

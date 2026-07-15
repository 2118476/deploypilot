package com.deploypilot.controller;

import com.deploypilot.dto.ApiResponse;
import com.deploypilot.dto.BlueprintResponse;
import com.deploypilot.service.DeploymentBlueprintService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/projects/{projectId}/blueprint")
public class DeploymentBlueprintController {

    private final DeploymentBlueprintService blueprintService;

    public DeploymentBlueprintController(DeploymentBlueprintService blueprintService) {
        this.blueprintService = blueprintService;
    }

    /** Generates (or regenerates) the blueprint from the latest successful analysis. */
    @PostMapping
    public ResponseEntity<ApiResponse<BlueprintResponse>> generate(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.ok("Blueprint generated", blueprintService.generate(projectId)));
    }

    /** Returns the most recent blueprint, flagged stale when a newer analysis exists. */
    @GetMapping
    public ResponseEntity<ApiResponse<BlueprintResponse>> latest(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.ok(blueprintService.getLatest(projectId)));
    }

    /**
     * Applies platform overrides (componentId -> platform) and recalculates the
     * blueprint. A blank value clears the override for that component.
     */
    @PutMapping("/overrides")
    public ResponseEntity<ApiResponse<BlueprintResponse>> override(@PathVariable Long projectId,
                                                                   @RequestBody Map<String, String> overrides) {
        return ResponseEntity.ok(ApiResponse.ok("Blueprint recalculated",
            blueprintService.applyOverrides(projectId, overrides)));
    }
}

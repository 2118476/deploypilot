package com.deploypilot.controller;

import com.deploypilot.dto.ApiResponse;
import com.deploypilot.dto.HealthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<ApiResponse<HealthResponse>> health() {
        HealthResponse h = new HealthResponse();
        h.setStatus("UP");
        h.setVersion("1.0.0");
        // Render injects RENDER_GIT_COMMIT at build and runtime. Exposing the short
        // commit makes the running version provable, so a stalled deploy is visible.
        String commit = System.getenv("RENDER_GIT_COMMIT");
        h.setCommit(commit == null || commit.isBlank()
            ? "unknown" : commit.substring(0, Math.min(7, commit.length())));
        h.setTimestamp(Instant.now());
        return ResponseEntity.ok(ApiResponse.ok(h));
    }
}

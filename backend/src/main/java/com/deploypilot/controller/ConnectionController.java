package com.deploypilot.controller;

import com.deploypilot.dto.ApiResponse;
import com.deploypilot.dto.ConnectRequest;
import com.deploypilot.dto.ConnectionResponse;
import com.deploypilot.model.enums.ProviderType;
import com.deploypilot.provider.model.HostingSite;
import com.deploypilot.provider.model.RepositorySummary;
import com.deploypilot.service.ConnectionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Per-user provider connections (GitHub, Netlify, Render). Tokens are validated,
 * encrypted and never returned; responses expose status and permissions only.
 */
@RestController
@RequestMapping("/connections")
public class ConnectionController {

    private final ConnectionService connectionService;

    public ConnectionController(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ConnectionResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(connectionService.list()));
    }

    @PostMapping("/{provider}")
    public ResponseEntity<ApiResponse<ConnectionResponse>> connect(
            @PathVariable String provider, @Valid @RequestBody ConnectRequest request) {
        ProviderType type = parseProvider(provider);
        ConnectionResponse result = connectionService.connect(type, request.getConnectionType(), request.getToken());
        return ResponseEntity.ok(ApiResponse.ok(type + " connected", result));
    }

    @DeleteMapping("/{provider}")
    public ResponseEntity<ApiResponse<Void>> disconnect(@PathVariable String provider) {
        connectionService.disconnect(parseProvider(provider));
        return ResponseEntity.ok(ApiResponse.ok("Disconnected", null));
    }

    /** Repositories the connected GitHub account can access (no tokens exposed). */
    @GetMapping("/github/repositories")
    public ResponseEntity<ApiResponse<List<RepositorySummary>>> repositories() {
        return ResponseEntity.ok(ApiResponse.ok(connectionService.listRepositories()));
    }

    /** Existing sites/services for a hosting provider connection. */
    @GetMapping("/{provider}/sites")
    public ResponseEntity<ApiResponse<List<HostingSite>>> sites(@PathVariable String provider) {
        return ResponseEntity.ok(ApiResponse.ok(connectionService.listSites(parseProvider(provider))));
    }

    private ProviderType parseProvider(String provider) {
        try {
            return ProviderType.valueOf(provider.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown provider: " + provider);
        }
    }
}

package com.deploypilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Establishes a provider connection. The {@code token} is a provider-issued
 * personal access token / API key (or OAuth/App token). It is validated against
 * the provider, encrypted at rest and never returned.
 */
public class ConnectRequest {

    @NotBlank(message = "A provider token or API key is required")
    @Size(max = 500, message = "Token is too long")
    private String token;

    /** Optional; defaults to the provider's token variant when omitted. */
    @Size(max = 30)
    private String connectionType;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getConnectionType() { return connectionType; }
    public void setConnectionType(String connectionType) { this.connectionType = connectionType; }
}

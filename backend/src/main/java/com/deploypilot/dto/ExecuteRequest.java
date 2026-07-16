package com.deploypilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Consumes a confirmation to start (or retry) an automation run. */
public class ExecuteRequest {

    @NotBlank(message = "A confirmation code is required")
    @Size(max = 80)
    private String nonce;

    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }
}

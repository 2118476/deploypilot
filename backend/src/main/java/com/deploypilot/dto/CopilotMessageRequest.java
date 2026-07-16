package com.deploypilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A user's question to the Copilot. */
public class CopilotMessageRequest {

    @NotBlank(message = "A message is required")
    @Size(max = 2000, message = "Message is too long")
    private String message;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}

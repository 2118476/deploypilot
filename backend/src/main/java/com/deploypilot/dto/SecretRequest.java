package com.deploypilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Stores or replaces a user-supplied deployment secret. The value is write-only. */
public class SecretRequest {

    @NotBlank(message = "Variable name is required")
    @Size(max = 200)
    @Pattern(regexp = "[A-Za-z_][A-Za-z0-9_]*", message = "Use a valid environment variable name")
    private String name;

    @NotBlank(message = "A value is required")
    @Size(max = 8000, message = "Value is too long")
    private String value;

    @Size(max = 60)
    private String destination;

    public String getName() { return name; } public void setName(String n) { this.name = n; }
    public String getValue() { return value; } public void setValue(String v) { this.value = v; }
    public String getDestination() { return destination; } public void setDestination(String d) { this.destination = d; }
}

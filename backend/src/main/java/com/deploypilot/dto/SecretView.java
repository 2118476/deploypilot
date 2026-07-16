package com.deploypilot.dto;

import java.time.Instant;

/** Masked view of a stored secret. Never carries the value. */
public class SecretView {
    private String name;
    private String destination;
    private boolean hasValue = true;
    private final String masked = "••••••••";
    private Instant updatedAt;

    public String getName() { return name; } public void setName(String n) { this.name = n; }
    public String getDestination() { return destination; } public void setDestination(String d) { this.destination = d; }
    public boolean isHasValue() { return hasValue; } public void setHasValue(boolean h) { this.hasValue = h; }
    public String getMasked() { return masked; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant u) { this.updatedAt = u; }
}

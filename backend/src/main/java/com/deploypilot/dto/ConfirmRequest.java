package com.deploypilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Requests a confirmation for a specific plan. Carries the same inputs used to
 * generate the reviewed plan plus the {@code planHash} the client saw, so the
 * server can reproduce the exact plan and reject it if anything has drifted.
 */
public class ConfirmRequest extends PlanRequest {

    @NotBlank(message = "planHash is required")
    @Size(max = 64)
    private String planHash;

    public String getPlanHash() { return planHash; }
    public void setPlanHash(String planHash) { this.planHash = planHash; }
}

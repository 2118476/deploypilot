package com.deploypilot.dto;

import com.deploypilot.model.enums.StepStatus;

public class StepProgressRequest {
    private StepStatus status;
    private String note;

    public StepStatus getStatus() { return status; }
    public void setStatus(StepStatus status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}

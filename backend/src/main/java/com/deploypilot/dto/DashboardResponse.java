package com.deploypilot.dto;

import java.util.List;

public class DashboardResponse {
    private List<ProjectSummaryDto> projects;
    private int totalProjects;
    private int completedSteps;
    private int totalSteps;
    private int bookmarkCount;
    private String nextStepTitle;
    private String nextStepAction;

    public List<ProjectSummaryDto> getProjects() { return projects; } public void setProjects(List<ProjectSummaryDto> p) { this.projects = p; }
    public int getTotalProjects() { return totalProjects; } public void setTotalProjects(int t) { this.totalProjects = t; }
    public int getCompletedSteps() { return completedSteps; } public void setCompletedSteps(int c) { this.completedSteps = c; }
    public int getTotalSteps() { return totalSteps; } public void setTotalSteps(int t) { this.totalSteps = t; }
    public int getBookmarkCount() { return bookmarkCount; } public void setBookmarkCount(int b) { this.bookmarkCount = b; }
    public String getNextStepTitle() { return nextStepTitle; } public void setNextStepTitle(String n) { this.nextStepTitle = n; }
    public String getNextStepAction() { return nextStepAction; } public void setNextStepAction(String n) { this.nextStepAction = n; }
}

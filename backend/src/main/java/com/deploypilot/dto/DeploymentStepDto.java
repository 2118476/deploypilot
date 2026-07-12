package com.deploypilot.dto;

import com.deploypilot.model.enums.StepStatus;
import java.time.Instant;
import java.util.List;

public class DeploymentStepDto {
    private int orderIndex;
    private String title;
    private String description;
    private String category;
    private String whatToDo;
    private String whyNecessary;
    private String whereToDoIt;
    private String commandOrValue;
    private String whatCommandDoes;
    private String expectedResult;
    private List<String> commonErrors;
    private String securityWarning;
    private String completionControls;
    private String nextAction;
    private StepStatus status;
    private boolean bookmarked;
    private String personalNote;
    private Instant completedAt;

    public int getOrderIndex() { return orderIndex; } public void setOrderIndex(int i) { this.orderIndex = i; }
    public String getTitle() { return title; } public void setTitle(String t) { this.title = t; }
    public String getDescription() { return description; } public void setDescription(String d) { this.description = d; }
    public String getCategory() { return category; } public void setCategory(String c) { this.category = c; }
    public String getWhatToDo() { return whatToDo; } public void setWhatToDo(String w) { this.whatToDo = w; }
    public String getWhyNecessary() { return whyNecessary; } public void setWhyNecessary(String w) { this.whyNecessary = w; }
    public String getWhereToDoIt() { return whereToDoIt; } public void setWhereToDoIt(String w) { this.whereToDoIt = w; }
    public String getCommandOrValue() { return commandOrValue; } public void setCommandOrValue(String c) { this.commandOrValue = c; }
    public String getWhatCommandDoes() { return whatCommandDoes; } public void setWhatCommandDoes(String w) { this.whatCommandDoes = w; }
    public String getExpectedResult() { return expectedResult; } public void setExpectedResult(String e) { this.expectedResult = e; }
    public List<String> getCommonErrors() { return commonErrors; } public void setCommonErrors(List<String> c) { this.commonErrors = c; }
    public String getSecurityWarning() { return securityWarning; } public void setSecurityWarning(String s) { this.securityWarning = s; }
    public String getCompletionControls() { return completionControls; } public void setCompletionControls(String c) { this.completionControls = c; }
    public String getNextAction() { return nextAction; } public void setNextAction(String n) { this.nextAction = n; }
    public StepStatus getStatus() { return status; } public void setStatus(StepStatus s) { this.status = s; }
    public boolean isBookmarked() { return bookmarked; } public void setBookmarked(boolean b) { this.bookmarked = b; }
    public String getPersonalNote() { return personalNote; } public void setPersonalNote(String p) { this.personalNote = p; }
    public Instant getCompletedAt() { return completedAt; } public void setCompletedAt(Instant c) { this.completedAt = c; }
}

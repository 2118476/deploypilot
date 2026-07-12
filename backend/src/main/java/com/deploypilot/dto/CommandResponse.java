package com.deploypilot.dto;

public class CommandResponse {
    private Long id;
    private String category;
    private String title;
    private String command;
    private String description;
    private String explanation;
    private String warning;
    private boolean destructive;
    private boolean beginnerMode;

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getCategory() { return category; } public void setCategory(String c) { this.category = c; }
    public String getTitle() { return title; } public void setTitle(String t) { this.title = t; }
    public String getCommand() { return command; } public void setCommand(String c) { this.command = c; }
    public String getDescription() { return description; } public void setDescription(String d) { this.description = d; }
    public String getExplanation() { return explanation; } public void setExplanation(String e) { this.explanation = e; }
    public String getWarning() { return warning; } public void setWarning(String w) { this.warning = w; }
    public boolean isDestructive() { return destructive; } public void setDestructive(boolean d) { this.destructive = d; }
    public boolean isBeginnerMode() { return beginnerMode; } public void setBeginnerMode(boolean b) { this.beginnerMode = b; }
}

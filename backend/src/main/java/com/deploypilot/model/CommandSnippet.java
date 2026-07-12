package com.deploypilot.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "command_snippets")
public class CommandSnippet {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 50) private String category;
    @Column(nullable = false, length = 150) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String command;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(columnDefinition = "TEXT") private String explanation;
    @Column(columnDefinition = "TEXT") private String warning;
    @Column(name = "is_destructive", nullable = false) private boolean isDestructive = false;
    @Column(name = "beginner_mode", nullable = false) private boolean beginnerMode = true;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    public CommandSnippet() {}
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getCategory() { return category; } public void setCategory(String category) { this.category = category; }
    public String getTitle() { return title; } public void setTitle(String title) { this.title = title; }
    public String getCommand() { return command; } public void setCommand(String command) { this.command = command; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
    public String getExplanation() { return explanation; } public void setExplanation(String explanation) { this.explanation = explanation; }
    public String getWarning() { return warning; } public void setWarning(String warning) { this.warning = warning; }
    public boolean isDestructive() { return isDestructive; } public void setDestructive(boolean destructive) { isDestructive = destructive; }
    public boolean isBeginnerMode() { return beginnerMode; } public void setBeginnerMode(boolean beginnerMode) { this.beginnerMode = beginnerMode; }
    public Instant getCreatedAt() { return createdAt; }
}

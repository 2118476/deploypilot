package com.deploypilot.dto;

import com.deploypilot.model.enums.GuideDifficulty;
import java.time.Instant;
import java.util.List;

public class GuideResponse {
    private Long id;
    private Long categoryId;
    private String title;
    private String slug;
    private String description;
    private String content;
    private GuideDifficulty difficulty;
    private List<GuideSectionDto> sections;
    private Instant createdAt;

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getCategoryId() { return categoryId; } public void setCategoryId(Long c) { this.categoryId = c; }
    public String getTitle() { return title; } public void setTitle(String t) { this.title = t; }
    public String getSlug() { return slug; } public void setSlug(String s) { this.slug = s; }
    public String getDescription() { return description; } public void setDescription(String d) { this.description = d; }
    public String getContent() { return content; } public void setContent(String c) { this.content = c; }
    public GuideDifficulty getDifficulty() { return difficulty; } public void setDifficulty(GuideDifficulty d) { this.difficulty = d; }
    public List<GuideSectionDto> getSections() { return sections; } public void setSections(List<GuideSectionDto> s) { this.sections = s; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant c) { this.createdAt = c; }
}

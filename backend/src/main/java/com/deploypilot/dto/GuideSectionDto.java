package com.deploypilot.dto;

public class GuideSectionDto {
    private Long id;
    private Long guideId;
    private String title;
    private String content;
    private Integer sortOrder;

    public GuideSectionDto() {}
    public GuideSectionDto(Long id, Long guideId, String title, String content, Integer sortOrder) {
        this.id = id; this.guideId = guideId; this.title = title; this.content = content; this.sortOrder = sortOrder;
    }
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getGuideId() { return guideId; } public void setGuideId(Long g) { this.guideId = g; }
    public String getTitle() { return title; } public void setTitle(String t) { this.title = t; }
    public String getContent() { return content; } public void setContent(String c) { this.content = c; }
    public Integer getSortOrder() { return sortOrder; } public void setSortOrder(Integer s) { this.sortOrder = s; }
}

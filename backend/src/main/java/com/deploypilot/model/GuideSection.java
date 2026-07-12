package com.deploypilot.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "guide_sections")
public class GuideSection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "guide_id", nullable = false) private Long guideId;
    @Column(length = 200) private String title;
    @Column(columnDefinition = "TEXT") private String content;
    @Column(name = "sort_order") private Integer sortOrder;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    public GuideSection() {}
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getGuideId() { return guideId; } public void setGuideId(Long guideId) { this.guideId = guideId; }
    public String getTitle() { return title; } public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; } public void setContent(String content) { this.content = content; }
    public Integer getSortOrder() { return sortOrder; } public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
}

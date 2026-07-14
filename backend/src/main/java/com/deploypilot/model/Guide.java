package com.deploypilot.model;

import com.deploypilot.model.enums.GuideDifficulty;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity
@Table(name = "guides")
public class Guide {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "category_id", nullable = false) private Long categoryId;
    @Column(nullable = false, length = 150) private String title;
    @Column(nullable = false, unique = true, length = 100) private String slug;
    @Column(length = 500) private String description;
    @Column(columnDefinition = "TEXT") private String content;
    @Enumerated(EnumType.STRING) @Column(length = 20) private GuideDifficulty difficulty;
    @Column(name = "sort_order") private Integer sortOrder;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    public Guide() {}
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getCategoryId() { return categoryId; } public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getTitle() { return title; } public void setTitle(String title) { this.title = title; }
    public String getSlug() { return slug; } public void setSlug(String slug) { this.slug = slug; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
    public String getContent() { return content; } public void setContent(String content) { this.content = content; }
    public GuideDifficulty getDifficulty() { return difficulty; } public void setDifficulty(GuideDifficulty difficulty) { this.difficulty = difficulty; }
    public Integer getSortOrder() { return sortOrder; } public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
}

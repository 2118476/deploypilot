package com.deploypilot.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "guide_categories")
public class GuideCategory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 50) private String name;
    @Column(nullable = false, unique = true, length = 50) private String slug;
    @Column(length = 255) private String description;
    @Column(length = 50) private String icon;
    @Column(name = "sort_order") private Integer sortOrder;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    public GuideCategory() {}
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; } public void setSlug(String slug) { this.slug = slug; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
    public String getIcon() { return icon; } public void setIcon(String icon) { this.icon = icon; }
    public Integer getSortOrder() { return sortOrder; } public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
}

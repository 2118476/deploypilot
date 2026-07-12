package com.deploypilot.dto;

public class GuideCategoryResponse {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private String icon;
    private Integer sortOrder;

    public GuideCategoryResponse() {}
    public GuideCategoryResponse(Long id, String name, String slug, String description, String icon, Integer sortOrder) {
        this.id = id; this.name = name; this.slug = slug; this.description = description; this.icon = icon; this.sortOrder = sortOrder;
    }
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getName() { return name; } public void setName(String n) { this.name = n; }
    public String getSlug() { return slug; } public void setSlug(String s) { this.slug = s; }
    public String getDescription() { return description; } public void setDescription(String d) { this.description = d; }
    public String getIcon() { return icon; } public void setIcon(String i) { this.icon = i; }
    public Integer getSortOrder() { return sortOrder; } public void setSortOrder(Integer s) { this.sortOrder = s; }
}

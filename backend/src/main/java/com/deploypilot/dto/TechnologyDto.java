package com.deploypilot.dto;

import com.deploypilot.model.enums.TechnologyCategory;

public class TechnologyDto {
    private Long id;
    private TechnologyCategory category;
    private String technology;
    private String version;

    public TechnologyDto() {}
    public TechnologyDto(Long id, TechnologyCategory category, String technology, String version) {
        this.id = id; this.category = category; this.technology = technology; this.version = version;
    }

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public TechnologyCategory getCategory() { return category; } public void setCategory(TechnologyCategory c) { this.category = c; }
    public String getTechnology() { return technology; } public void setTechnology(String t) { this.technology = t; }
    public String getVersion() { return version; } public void setVersion(String v) { this.version = v; }
}

package com.deploypilot.dto;

public class GlossaryTermResponse {
    private Long id;
    private String term;
    private String slug;
    private String definition;
    private String example;
    private String category;
    private String relatedTerms;

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getTerm() { return term; } public void setTerm(String t) { this.term = t; }
    public String getSlug() { return slug; } public void setSlug(String s) { this.slug = s; }
    public String getDefinition() { return definition; } public void setDefinition(String d) { this.definition = d; }
    public String getExample() { return example; } public void setExample(String e) { this.example = e; }
    public String getCategory() { return category; } public void setCategory(String c) { this.category = c; }
    public String getRelatedTerms() { return relatedTerms; } public void setRelatedTerms(String r) { this.relatedTerms = r; }
}

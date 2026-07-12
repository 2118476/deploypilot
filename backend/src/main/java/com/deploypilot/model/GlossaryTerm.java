package com.deploypilot.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity
@Table(name = "glossary_terms")
public class GlossaryTerm {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(length = 255)
    private String term;
    @Column(length = 255)
    private String slug;
    @Column(columnDefinition = "TEXT")
    private String definition;
    @Column(columnDefinition = "TEXT")
    private String example;
    @Column(length = 255)
    private String category;
    @Column(length = 255)
    private String relatedTerms;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public GlossaryTerm() {}
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getTerm() { return term; } public void setTerm(String term) { this.term = term; }
    public String getSlug() { return slug; } public void setSlug(String slug) { this.slug = slug; }
    public String getDefinition() { return definition; } public void setDefinition(String definition) { this.definition = definition; }
    public String getExample() { return example; } public void setExample(String example) { this.example = example; }
    public String getCategory() { return category; } public void setCategory(String category) { this.category = category; }
    public String getRelatedTerms() { return relatedTerms; } public void setRelatedTerms(String relatedTerms) { this.relatedTerms = relatedTerms; }
    public Instant getCreatedAt() { return createdAt; }
}

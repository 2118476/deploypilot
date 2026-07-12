package com.deploypilot.model;

import com.deploypilot.model.enums.EnvVarCategory;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "env_var_definitions")
public class EnvVarDefinition {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 100) private String name;
    @Column(length = 500) private String description;
    @Enumerated(EnumType.STRING) @Column(length = 30) private EnvVarCategory category;
    @Column(length = 50) private String platform;
    @Column(name = "local_file_location", length = 100) private String localFileLocation;
    @Column(name = "production_location", length = 100) private String productionLocation;
    @Column(nullable = false) private boolean required = false;
    @Column(name = "example_value", length = 200) private String exampleValue;
    @Column(name = "documentation_url", length = 255) private String documentationUrl;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    public EnvVarDefinition() {}
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
    public EnvVarCategory getCategory() { return category; } public void setCategory(EnvVarCategory category) { this.category = category; }
    public String getPlatform() { return platform; } public void setPlatform(String platform) { this.platform = platform; }
    public String getLocalFileLocation() { return localFileLocation; } public void setLocalFileLocation(String localFileLocation) { this.localFileLocation = localFileLocation; }
    public String getProductionLocation() { return productionLocation; } public void setProductionLocation(String productionLocation) { this.productionLocation = productionLocation; }
    public boolean isRequired() { return required; } public void setRequired(boolean required) { this.required = required; }
    public String getExampleValue() { return exampleValue; } public void setExampleValue(String exampleValue) { this.exampleValue = exampleValue; }
    public String getDocumentationUrl() { return documentationUrl; } public void setDocumentationUrl(String documentationUrl) { this.documentationUrl = documentationUrl; }
    public Instant getCreatedAt() { return createdAt; }
}

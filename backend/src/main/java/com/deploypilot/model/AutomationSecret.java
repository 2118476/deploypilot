package com.deploypilot.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * A user-supplied secret value needed for a deployment (e.g. a database password
 * or a provider-issued key), stored encrypted and only while it is needed. The
 * plaintext value is never returned to the client once saved.
 */
@Entity
@Table(name = "automation_secrets", uniqueConstraints = {
    @UniqueConstraint(name = "uq_secret_project_var", columnNames = {"project_id", "var_name"})
}, indexes = {
    @Index(name = "idx_secrets_project", columnList = "project_id")
})
public class AutomationSecret {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "var_name", nullable = false, length = 200) private String varName;
    @Column(name = "encrypted_value", nullable = false, columnDefinition = "TEXT") private String encryptedValue;
    @Column(name = "destination", length = 60) private String destination;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public AutomationSecret() {}

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long u) { this.userId = u; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long p) { this.projectId = p; }
    public String getVarName() { return varName; } public void setVarName(String v) { this.varName = v; }
    public String getEncryptedValue() { return encryptedValue; } public void setEncryptedValue(String e) { this.encryptedValue = e; }
    public String getDestination() { return destination; } public void setDestination(String d) { this.destination = d; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

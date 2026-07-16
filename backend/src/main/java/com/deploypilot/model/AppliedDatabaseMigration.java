package com.deploypilot.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Records a repository-owned migration that has been applied to a specific
 * Supabase project, with its file checksum, so retries never reapply it.
 */
@Entity
@Table(name = "applied_database_migrations", uniqueConstraints = {
    @UniqueConstraint(name = "uq_applied_migration", columnNames = {"project_id", "supabase_project_ref", "migration_name"})
}, indexes = {
    @Index(name = "idx_applied_migrations_project", columnList = "project_id")
})
public class AppliedDatabaseMigration {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "supabase_project_ref", nullable = false, length = 120) private String supabaseProjectRef;
    @Column(name = "migration_name", nullable = false, length = 300) private String migrationName;
    @Column(name = "checksum", nullable = false, length = 80) private String checksum;
    @CreationTimestamp @Column(name = "applied_at", nullable = false, updatable = false) private Instant appliedAt;

    public AppliedDatabaseMigration() {}

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long u) { this.userId = u; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long p) { this.projectId = p; }
    public String getSupabaseProjectRef() { return supabaseProjectRef; } public void setSupabaseProjectRef(String s) { this.supabaseProjectRef = s; }
    public String getMigrationName() { return migrationName; } public void setMigrationName(String m) { this.migrationName = m; }
    public String getChecksum() { return checksum; } public void setChecksum(String c) { this.checksum = c; }
    public Instant getAppliedAt() { return appliedAt; }
}

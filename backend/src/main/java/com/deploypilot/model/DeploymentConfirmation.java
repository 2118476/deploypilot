package com.deploypilot.model;

import com.deploypilot.model.enums.AutomationMode;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A short-lived, single-use confirmation that authorises exactly one action
 * plan. It binds the approval to the user, project, provider accounts, plan
 * hash and repository/commit, and expires. Once {@code consumedAt} is set it
 * cannot be replayed; if the plan hash no longer matches, execution is refused.
 */
@Entity
@Table(name = "deployment_confirmations", uniqueConstraints = {
    @UniqueConstraint(name = "uq_confirmation_nonce", columnNames = {"nonce"})
}, indexes = {
    @Index(name = "idx_confirmations_project", columnList = "project_id")
})
public class DeploymentConfirmation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "automation_run_id") private Long automationRunId;
    @Column(name = "nonce", nullable = false, length = 80) private String nonce;
    @Column(name = "plan_hash", nullable = false, length = 64) private String planHash;
    @Enumerated(EnumType.STRING) @Column(name = "mode", nullable = false, length = 20) private AutomationMode mode;
    @Column(name = "repository_full_name", length = 200) private String repositoryFullName;
    @Column(name = "commit_sha", length = 80) private String commitSha;
    @Column(name = "account_binding", length = 500) private String accountBinding;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "consumed_at") private Instant consumedAt;

    public DeploymentConfirmation() {}

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long u) { this.userId = u; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long p) { this.projectId = p; }
    public Long getAutomationRunId() { return automationRunId; } public void setAutomationRunId(Long a) { this.automationRunId = a; }
    public String getNonce() { return nonce; } public void setNonce(String n) { this.nonce = n; }
    public String getPlanHash() { return planHash; } public void setPlanHash(String p) { this.planHash = p; }
    public AutomationMode getMode() { return mode; } public void setMode(AutomationMode m) { this.mode = m; }
    public String getRepositoryFullName() { return repositoryFullName; } public void setRepositoryFullName(String r) { this.repositoryFullName = r; }
    public String getCommitSha() { return commitSha; } public void setCommitSha(String c) { this.commitSha = c; }
    public String getAccountBinding() { return accountBinding; } public void setAccountBinding(String a) { this.accountBinding = a; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; } public void setExpiresAt(Instant e) { this.expiresAt = e; }
    public Instant getConsumedAt() { return consumedAt; } public void setConsumedAt(Instant c) { this.consumedAt = c; }
}

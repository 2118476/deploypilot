package com.deploypilot.model;

import com.deploypilot.model.enums.TokenType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity
@Table(name = "notification_tokens")
public class NotificationToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false, length = 500) private String token;
    @Enumerated(EnumType.STRING) @Column(name = "device_type", nullable = false, length = 20) private TokenType deviceType = TokenType.WEB;
    @Column(nullable = false) private boolean active = true;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "last_used_at") private Instant lastUsedAt;

    public NotificationToken() {}
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public String getToken() { return token; } public void setToken(String token) { this.token = token; }
    public TokenType getDeviceType() { return deviceType; } public void setDeviceType(TokenType deviceType) { this.deviceType = deviceType; }
    public boolean isActive() { return active; } public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastUsedAt() { return lastUsedAt; } public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}

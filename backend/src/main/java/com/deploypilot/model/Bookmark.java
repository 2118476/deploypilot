package com.deploypilot.model;

import com.deploypilot.model.enums.BookmarkItemType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "bookmarks")
public class Bookmark {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long userId;
    @Enumerated(EnumType.STRING) @Column(name = "item_type", nullable = false, length = 30)
    private BookmarkItemType itemType;
    private Long itemId;
    @Column(length = 255)
    private String note;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public Bookmark() {}
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public BookmarkItemType getItemType() { return itemType; } public void setItemType(BookmarkItemType itemType) { this.itemType = itemType; }
    public Long getItemId() { return itemId; } public void setItemId(Long itemId) { this.itemId = itemId; }
    public String getNote() { return note; } public void setNote(String note) { this.note = note; }
    public Instant getCreatedAt() { return createdAt; }
}

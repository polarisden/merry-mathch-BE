package com.fsd10.merry_match_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

  @Id
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(name = "chat_room_id", nullable = false, columnDefinition = "uuid")
  private UUID chatRoomId;

  @Column(name = "sender_id", nullable = false, columnDefinition = "uuid")
  private UUID senderId;

  /** text | image */
  @Column(name = "message_type", nullable = false, length = 32)
  private String messageType;

  @Column(name = "message_text", columnDefinition = "TEXT")
  private String messageText;

  @Column(name = "image_url", columnDefinition = "TEXT")
  private String imageUrl;

  @Column(name = "is_read", nullable = false)
  private boolean isRead;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    if (this.createdAt == null) {
      this.createdAt = Instant.now();
    }
    if (this.id == null) {
      this.id = UUID.randomUUID();
    }
  }
}

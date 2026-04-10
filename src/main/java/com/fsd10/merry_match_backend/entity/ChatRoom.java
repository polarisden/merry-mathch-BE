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
@Table(name = "chat_rooms")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoom {

  @Id
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(name = "match_id", nullable = false, columnDefinition = "uuid")
  private UUID matchId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "last_message_text", columnDefinition = "TEXT")
  private String lastMessageText;

  @Column(name = "last_message_type", length = 32)
  private String lastMessageType;

  @Column(name = "last_message_at")
  private Instant lastMessageAt;

  @Column(name = "last_sender_id", columnDefinition = "uuid")
  private UUID lastSenderId;

  @PrePersist
  void onCreate() {
    if (this.createdAt == null) {
      this.createdAt = Instant.now();
    }
  }
}

package com.fsd10.merry_match_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "profile_images")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileImage {

  @Id
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "image_url", nullable = false, length = 2048)
  private String imageUrl;

  @Column(name = "is_primary", nullable = false)
  private boolean isPrimary;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    if (this.createdAt == null) {
      this.createdAt = Instant.now();
    }
  }
}


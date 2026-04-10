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
@Table(name = "matches")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Match {

  @Id
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(name = "user1_id", nullable = false, columnDefinition = "uuid")
  private UUID user1Id;

  @Column(name = "user2_id", nullable = false, columnDefinition = "uuid")
  private UUID user2Id;

  @Column(name = "matched_at", nullable = false)
  private Instant matchedAt;

  /** e.g. active, unmatched */
  @Column(nullable = false, length = 32)
  private String status;

  @PrePersist
  void onCreate() {
    if (this.matchedAt == null) {
      this.matchedAt = Instant.now();
    }
    if (this.status == null || this.status.isBlank()) {
      this.status = "active";
    }
  }
}

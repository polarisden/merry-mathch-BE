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
@Table(name = "user_interests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInterest {

  @Id
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
  private UUID userId;

  @Column(name = "interest_id", nullable = false, columnDefinition = "uuid")
  private UUID interestId;

  @Column(name = "created_at")
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

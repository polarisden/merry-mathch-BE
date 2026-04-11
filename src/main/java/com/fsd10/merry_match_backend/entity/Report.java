package com.fsd10.merry_match_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import org.hibernate.annotations.JdbcType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {

  @Id
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(name = "reporter_id", nullable = false, columnDefinition = "uuid")
  private UUID reporterId;

  @Column(name = "issue", nullable = false, length = 500)
  private String issue;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "status", nullable = false, columnDefinition = "report_status")
  @Convert(converter = ReportStatusConverter.class)
  @JdbcType(ReportStatusPostgreSqlJdbcType.class)
  @Builder.Default
  private ReportStatus status = ReportStatus.NEW;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    if (this.id == null) this.id = UUID.randomUUID();
    Instant now = Instant.now();
    if (this.createdAt == null) this.createdAt = now;
    if (this.updatedAt == null) this.updatedAt = now;
    if (this.status == null) this.status = ReportStatus.NEW;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }
}

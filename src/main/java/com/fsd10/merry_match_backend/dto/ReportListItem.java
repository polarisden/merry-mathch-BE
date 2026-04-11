package com.fsd10.merry_match_backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ReportListItem {
  private UUID id;
  private String reporterName;
  private String issue;
  private String description;
  private String status;
  private Instant createdAt;
}

package com.fsd10.merry_match_backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ProfileImageUploadResponse {
  private UUID id;
  private UUID userId;
  private String imageUrl;
  private boolean isPrimary;
  private Instant createdAt;
}


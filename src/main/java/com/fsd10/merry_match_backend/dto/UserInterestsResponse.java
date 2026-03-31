package com.fsd10.merry_match_backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class UserInterestsResponse {
  private UUID userId;
  private List<UUID> interestIds;
}

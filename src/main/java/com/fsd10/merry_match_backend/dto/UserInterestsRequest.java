package com.fsd10.merry_match_backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class UserInterestsRequest {
  @NotNull
  private List<UUID> interestIds;
}

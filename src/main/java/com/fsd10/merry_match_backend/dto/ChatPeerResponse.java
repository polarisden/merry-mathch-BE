package com.fsd10.merry_match_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ChatPeerResponse {

  @JsonProperty("user_id")
  private UUID userId;

  private String name;

  @JsonProperty("image_url")
  private String imageUrl;
}

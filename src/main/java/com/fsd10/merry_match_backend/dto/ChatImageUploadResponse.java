package com.fsd10.merry_match_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatImageUploadResponse {

  @JsonProperty("image_url")
  private String imageUrl;
}

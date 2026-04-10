package com.fsd10.merry_match_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SendChatMessageRequest {

  @JsonProperty("message_type")
  private String messageType;

  @JsonProperty("message_text")
  private String messageText;

  @JsonProperty("image_url")
  private String imageUrl;
}

package com.fsd10.merry_match_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ChatMessageResponse {

  private UUID id;

  @JsonProperty("chat_room_id")
  private UUID chatRoomId;

  @JsonProperty("sender_id")
  private UUID senderId;

  @JsonProperty("message_type")
  private String messageType;

  @JsonProperty("message_text")
  private String messageText;

  @JsonProperty("image_url")
  private String imageUrl;

  @JsonProperty("is_read")
  private boolean isRead;

  @JsonProperty("created_at")
  private Instant createdAt;
}

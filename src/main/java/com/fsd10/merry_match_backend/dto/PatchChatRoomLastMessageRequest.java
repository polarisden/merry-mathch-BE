package com.fsd10.merry_match_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
public class PatchChatRoomLastMessageRequest {

  @JsonProperty("last_message_text")
  private String lastMessageText;

  @JsonProperty("last_message_type")
  private String lastMessageType;

  @JsonProperty("last_message_at")
  private Instant lastMessageAt;

  @JsonProperty("last_sender_id")
  private UUID lastSenderId;
}

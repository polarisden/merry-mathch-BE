package com.fsd10.merry_match_backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SendChatMessageResponse {
  private ChatMessageResponse message;
}

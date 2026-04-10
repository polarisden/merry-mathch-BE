package com.fsd10.merry_match_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * One row for GET /api/chat/matches/{matchId}/rooms — matches frontend {@code normalizeChatRoom}.
 */
public record ChatRoomListItem(
    UUID id,
    @JsonProperty("match_id") UUID matchId,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("last_message_text") String lastMessageText,
    @JsonProperty("last_message_type") String lastMessageType,
    @JsonProperty("last_message_at") Instant lastMessageAt,
    @JsonProperty("last_sender_id") UUID lastSenderId,
    @JsonProperty("peer_name") String peerName,
    @JsonProperty("peer_image_url") String peerImageUrl,
    /** Messages from the peer with {@code is_read = false} for the listing user. */
    @JsonProperty("unread_count") long unreadCount) {}

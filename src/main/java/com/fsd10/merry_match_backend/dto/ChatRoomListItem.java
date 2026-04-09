package com.fsd10.merry_match_backend.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatRoomListItem(
    UUID id,
    UUID matchId,
    /** The matched user (not the current user). */
    UUID otherUserId,
    Instant createdAt
) {}

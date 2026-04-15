package com.fsd10.merry_match_backend.dto;

import java.time.Instant;
import java.util.UUID;

public record MatchResponse(
        UUID id,
        UUID user1Id,
        UUID user2Id,
        Instant matchedAt,
        String status
) {}

package com.fsd10.merry_match_backend.dto;

import java.util.UUID;

public record MerryActionResponse(
    UUID id,
    UUID userId,
    UUID matchedUserId,
    String status
) {
}

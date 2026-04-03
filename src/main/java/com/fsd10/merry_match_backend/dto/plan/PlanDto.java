package com.fsd10.merry_match_backend.dto.plan;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Builder;

@Builder
public record PlanDto(
        UUID id,
        String name,
        Integer priceSatang,
        Integer swipeLimit,
        Boolean canSeeLikers,
        Integer sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<PlanDescriptionDto> descriptions
) {}


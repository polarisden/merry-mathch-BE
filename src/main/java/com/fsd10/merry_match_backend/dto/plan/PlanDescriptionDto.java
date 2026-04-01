package com.fsd10.merry_match_backend.dto.plan;

import java.util.UUID;

import lombok.Builder;

@Builder
public record PlanDescriptionDto(
        UUID id,
        String description,
        Integer sortOrder
) {}


package com.fsd10.merry_match_backend.dto.plan;

import jakarta.validation.constraints.NotBlank;

public record PlanDescriptionUpsertRequest(
        @NotBlank(message = "description is required") String description,
        Integer sortOrder
) {}

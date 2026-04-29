package com.fsd10.merry_match_backend.dto.plan;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record PlanUpsertRequest(
        @NotBlank(message = "name is required") String name,
        @NotNull(message = "merryLimit is required") Integer merryLimit,
        Integer priceSatang,
        Boolean canSeeLikers,
        Integer sortOrder,
        @Valid @NotEmpty(message = "details must contain at least one item") List<PlanDescriptionUpsertRequest> details
) {}

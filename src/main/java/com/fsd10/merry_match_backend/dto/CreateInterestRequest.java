package com.fsd10.merry_match_backend.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateInterestRequest(
    @NotBlank String name
) {}


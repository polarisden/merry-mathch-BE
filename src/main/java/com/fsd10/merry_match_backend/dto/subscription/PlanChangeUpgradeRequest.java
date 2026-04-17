package com.fsd10.merry_match_backend.dto.subscription;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PlanChangeUpgradeRequest(
        @NotNull UUID planId,
        @NotBlank String omiseToken
) {}

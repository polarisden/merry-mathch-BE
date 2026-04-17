package com.fsd10.merry_match_backend.dto.subscription;

import java.util.UUID;

import lombok.Builder;

@Builder
public record PlanBankItemDto(
        UUID planId,
        String planName,
        Integer planPriceSatang,
        Integer remainingDays
) {}

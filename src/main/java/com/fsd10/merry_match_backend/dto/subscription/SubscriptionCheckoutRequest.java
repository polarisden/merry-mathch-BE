package com.fsd10.merry_match_backend.dto.subscription;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubscriptionCheckoutRequest(
        @NotNull UUID planId,
        /**
         * One-time card token from Omise.js (tokn_test_... / tokn_live_...).
         */
        @NotBlank String omiseToken
) {}

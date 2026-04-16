package com.fsd10.merry_match_backend.dto.subscription;

import jakarta.validation.constraints.NotBlank;

/**
 * One-time card token from Omise.js ({@code tokn_...}) to attach as the new default card.
 */
public record UpdatePaymentCardRequest(
        @NotBlank String omiseToken
) {}

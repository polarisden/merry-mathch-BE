package com.fsd10.merry_match_backend.dto.subscription;

import java.util.UUID;

import lombok.Builder;

/**
 * @param status paid = persisted subscription + billing; pending = 3DS / async; failed = declined
 */
@Builder
public record SubscriptionCheckoutResponse(
        UUID subscriptionId,
        String chargeId,
        String status,
        String authorizeUri,
        String omiseCustomerId,
        PaymentCardDto paymentCard
) {}

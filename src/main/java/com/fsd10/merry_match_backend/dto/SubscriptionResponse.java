package com.fsd10.merry_match_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fsd10.merry_match_backend.dto.plan.PlanDto;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        @JsonProperty("user_id") UUID userId,
        PlanDto plan,
        String status,
        @JsonProperty("current_period_start") Instant currentPeriodStart,
        @JsonProperty("current_period_end") Instant currentPeriodEnd,
        @JsonProperty("cancel_at") Instant cancelAt,
        @JsonProperty("cancelled_at") Instant cancelledAt,
        @JsonProperty("auto_renew") Boolean autoRenew,
        @JsonProperty("card_brand") String cardBrand,
        @JsonProperty("card_expiration_month") Integer cardExpirationMonth,
        @JsonProperty("card_expiration_year") Integer cardExpirationYear,
        @JsonProperty("card_last_digits") String cardLastDigits,
        @JsonProperty("scheduled_plan_change_at") Instant scheduledPlanChangeAt,
        @JsonProperty("pending_plan_id") UUID pendingPlanId,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt
) {}

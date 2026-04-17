package com.fsd10.merry_match_backend.dto.subscription;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.fsd10.merry_match_backend.dto.plan.PlanDto;

import lombok.Builder;

/**
 * Current subscription for the authenticated user (success page / account).
 * {@code nextBillingDate} mirrors {@code currentPeriodEnd} for UI copy ("next charge" at period boundary).
 */
@Builder
public record SubscriptionDetailDto(
        UUID id,
        String status,
        LocalDateTime currentPeriodStart,
        LocalDateTime currentPeriodEnd,
        LocalDateTime nextBillingDate,
        LocalDateTime cancelAt,
        LocalDateTime cancelledAt,
        Boolean autoRenew,
        LocalDateTime createdAt,
        PlanDto plan,
        PaymentCardDto paymentCard,
        PlanDto pendingPlan,
        LocalDateTime scheduledPlanChangeAt,
        Integer currentPlanBankedDays,
        List<PlanBankItemDto> bankedPlans
) {}

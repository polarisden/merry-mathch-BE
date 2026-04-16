package com.fsd10.merry_match_backend.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fsd10.merry_match_backend.dto.plan.PlanDto;
import com.fsd10.merry_match_backend.dto.subscription.PaymentCardDto;
import com.fsd10.merry_match_backend.dto.subscription.SubscriptionDetailDto;
import com.fsd10.merry_match_backend.entity.Subscription;
import com.fsd10.merry_match_backend.exception.SubscriptionNotFoundException;
import com.fsd10.merry_match_backend.repository.SubscriptionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubscriptionReadService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlansService plansService;
    private final SubscriptionLifecycleService subscriptionLifecycleService;

    @Transactional(readOnly = true)
    public SubscriptionDetailDto getSubscriptionForUser(UUID subscriptionId, UUID userId) {
        subscriptionLifecycleService.applyPendingPlanChangesIfDue(userId);
        Subscription sub = subscriptionRepository.findByIdAndUser_Id(subscriptionId, userId)
                .orElseThrow(SubscriptionNotFoundException::new);
        return toDetail(sub);
    }

    /**
     * The user's subscription row (at most one per user in current schema).
     */
    @Transactional(readOnly = true)
    public Optional<SubscriptionDetailDto> findCurrentMembershipForUser(UUID userId) {
        subscriptionLifecycleService.applyPendingPlanChangesIfDue(userId);
        return subscriptionRepository.findByUser_Id(userId).map(this::toDetail);
    }

    private SubscriptionDetailDto toDetail(Subscription sub) {
        PlanDto plan = plansService.getPlanById(sub.getPlan().getId());
        PlanDto pendingPlan = null;
        if (sub.getPendingPlan() != null) {
            pendingPlan = plansService.getPlanById(sub.getPendingPlan().getId());
        }
        return SubscriptionDetailDto.builder()
                .id(sub.getId())
                .status(sub.getStatus().name())
                .currentPeriodStart(sub.getCurrentPeriodStart())
                .currentPeriodEnd(sub.getCurrentPeriodEnd())
                .nextBillingDate(sub.getCurrentPeriodEnd())
                .cancelAt(sub.getCancelAt())
                .cancelledAt(sub.getCancelledAt())
                .autoRenew(sub.getAutoRenew())
                .createdAt(sub.getCreatedAt())
                .plan(plan)
                .paymentCard(PaymentCardDto.fromSubscription(sub))
                .pendingPlan(pendingPlan)
                .scheduledPlanChangeAt(sub.getScheduledPlanChangeAt())
                .build();
    }
}

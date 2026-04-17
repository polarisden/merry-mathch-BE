package com.fsd10.merry_match_backend.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fsd10.merry_match_backend.dto.subscription.PlanChangePreviewResponse;
import com.fsd10.merry_match_backend.dto.subscription.SubscriptionCheckoutResponse;
import com.fsd10.merry_match_backend.entity.Plans;
import com.fsd10.merry_match_backend.entity.Subscription;
import com.fsd10.merry_match_backend.exception.InvalidPlanChangeException;
import com.fsd10.merry_match_backend.repository.PlansRepository;
import com.fsd10.merry_match_backend.repository.SubscriptionRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubscriptionPlanChangeService {

    private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");

    private final SubscriptionRepository subscriptionRepository;
    private final PlansRepository plansRepository;
    private final SubscriptionLifecycleService subscriptionLifecycleService;
    private final OmiseSubscriptionService omiseSubscriptionService;
    private final SubscriptionDayBankService subscriptionDayBankService;

    @Transactional(readOnly = true)
    public PlanChangePreviewResponse preview(UUID userId, UUID targetPlanId) {
        subscriptionLifecycleService.applyPendingPlanChangesIfDue(userId);
        Subscription sub = subscriptionRepository.findByUser_Id(userId)
                .orElseThrow(() -> new EntityNotFoundException("No subscription for user"));
        assertActiveForChange(sub);

        Plans current = sub.getPlan();
        Plans target = plansRepository.findById(targetPlanId)
                .orElseThrow(() -> new EntityNotFoundException("Plan not found"));
        if (target.getPriceSatang() < current.getPriceSatang()) {
            target = resolveDowngradeTarget(current, target, userId);
        }

        if (current.getId().equals(target.getId())) {
            return PlanChangePreviewResponse.builder()
                    .changeType("SAME")
                    .currentPlanId(current.getId())
                    .currentPlanName(current.getName())
                    .targetPlanId(target.getId())
                    .targetPlanName(target.getName())
                    .chargeAmountSatang(0)
                    .immediateEffective(true)
                    .bankedDaysFromCurrentPlan(0)
                    .bankedDaysAvailableOnTargetPlan(0)
                    .description("Already on this plan")
                    .build();
        }
        int bankedFromCurrent = computeRemainingDaysCeil(LocalDateTime.now(BANGKOK), sub.getCurrentPeriodEnd());
        int bankedOnTarget = subscriptionDayBankService.getRemainingDays(userId, target.getId());
        String changeType = target.getPriceSatang() > current.getPriceSatang() ? "UPGRADE" : "DOWNGRADE";
        int chargeAmount = "UPGRADE".equals(changeType) ? target.getPriceSatang() : 0;
        return PlanChangePreviewResponse.builder()
                .changeType(changeType)
                .currentPlanId(current.getId())
                .currentPlanName(current.getName())
                .targetPlanId(target.getId())
                .targetPlanName(target.getName())
                .chargeAmountSatang(chargeAmount)
                .immediateEffective("UPGRADE".equals(changeType))
                .bankedDaysFromCurrentPlan(bankedFromCurrent)
                .bankedDaysAvailableOnTargetPlan(bankedOnTarget)
                .description("UPGRADE switches immediately with full charge. DOWNGRADE is scheduled at current period end.")
                .build();
    }

    @Transactional
    public void scheduleDowngrade(UUID userId, UUID targetPlanId) {
        subscriptionLifecycleService.applyPendingPlanChangesIfDue(userId);
        Subscription sub = subscriptionRepository.findByUser_Id(userId)
                .orElseThrow(() -> new EntityNotFoundException("No subscription for user"));
        assertActiveForChange(sub);

        Plans current = sub.getPlan();
        Plans requestedTarget = plansRepository.findById(targetPlanId)
                .orElseThrow(() -> new EntityNotFoundException("Plan not found"));
        Plans target = resolveDowngradeTarget(current, requestedTarget, userId);

        if (current.getId().equals(target.getId())) {
            throw new InvalidPlanChangeException("Already on this plan");
        }
        if (target.getPriceSatang() >= current.getPriceSatang()) {
            throw new InvalidPlanChangeException("Downgrade requires a cheaper plan than your current plan");
        }

        sub.setPendingPlan(target);
        sub.setScheduledPlanChangeAt(sub.getCurrentPeriodEnd());
        subscriptionRepository.save(sub);
    }

    /**
     * Cancel a previously scheduled downgrade (keep current plan).
     */
    @Transactional
    public void cancelScheduledDowngrade(UUID userId) {
        subscriptionLifecycleService.applyPendingPlanChangesIfDue(userId);
        Subscription sub = subscriptionRepository.findByUser_Id(userId)
                .orElseThrow(() -> new EntityNotFoundException("No subscription for user"));
        assertActiveForChange(sub);

        if (sub.getPendingPlan() == null || sub.getScheduledPlanChangeAt() == null) {
            throw new InvalidPlanChangeException("No scheduled downgrade to cancel");
        }

        sub.setPendingPlan(null);
        sub.setScheduledPlanChangeAt(null);
        subscriptionRepository.save(sub);
    }

    @Transactional
    public SubscriptionCheckoutResponse confirmUpgrade(
            UUID userId,
            UUID targetPlanId,
            String omiseToken
    ) throws co.omise.models.OmiseException, java.io.IOException {
        subscriptionLifecycleService.applyPendingPlanChangesIfDue(userId);
        Subscription sub = subscriptionRepository.findByUser_Id(userId)
                .orElseThrow(() -> new EntityNotFoundException("No subscription for user"));
        assertActiveForChange(sub);

        Plans current = sub.getPlan();
        Plans target = plansRepository.findById(targetPlanId)
                .orElseThrow(() -> new EntityNotFoundException("Plan not found"));

        if (current.getId().equals(target.getId())) {
            throw new InvalidPlanChangeException("Already on this plan");
        }
        if (target.getPriceSatang() <= current.getPriceSatang()) {
            throw new InvalidPlanChangeException("Upgrade requires a more expensive target plan");
        }

        return omiseSubscriptionService.executePlanUpgradeCharge(
                userId, sub.getId(), target.getId(), omiseToken, target.getPriceSatang());
    }

    private static void assertActiveForChange(Subscription sub) {
        if (sub.getStatus() != Subscription.SubscriptionStatus.ACTIVE) {
            throw new InvalidPlanChangeException("Subscription is not active");
        }
        LocalDateTime now = LocalDateTime.now(BANGKOK);
        if (sub.getCurrentPeriodEnd() == null || !sub.getCurrentPeriodEnd().isAfter(now)) {
            throw new InvalidPlanChangeException("Current billing period has ended");
        }
    }

    private static int computeRemainingDaysCeil(LocalDateTime now, LocalDateTime periodEnd) {
        if (periodEnd == null || !periodEnd.isAfter(now)) {
            return 0;
        }
        long remainingSeconds = java.time.Duration.between(now, periodEnd).getSeconds();
        return (int) Math.ceil(remainingSeconds / 86400.0d);
    }

    private Plans resolveDowngradeTarget(Plans current, Plans requestedTarget, UUID userId) {
        if (requestedTarget.getPriceSatang() >= current.getPriceSatang()) {
            throw new InvalidPlanChangeException("Downgrade requires a cheaper plan than your current plan");
        }
        return subscriptionDayBankService
                .findHighestPricedBankPlanBelowPrice(userId, current.getPriceSatang())
                .orElse(requestedTarget);
    }
}

package com.fsd10.merry_match_backend.service;

import java.time.Duration;
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
    /** Omise มักกำหนดขั้นต่ำ ~20 THB — ถ้า prorate ต่ำกว่านี้จะปัดขึ้น */
    private static final long MIN_UPGRADE_CHARGE_SATANG = 2000L;

    private final SubscriptionRepository subscriptionRepository;
    private final PlansRepository plansRepository;
    private final SubscriptionLifecycleService subscriptionLifecycleService;
    private final OmiseSubscriptionService omiseSubscriptionService;

    @Transactional(readOnly = true)
    public PlanChangePreviewResponse preview(UUID userId, UUID targetPlanId) {
        subscriptionLifecycleService.applyPendingPlanChangesIfDue(userId);
        Subscription sub = subscriptionRepository.findByUser_Id(userId)
                .orElseThrow(() -> new EntityNotFoundException("No subscription for user"));
        assertActiveForChange(sub);

        Plans current = sub.getPlan();
        Plans target = plansRepository.findById(targetPlanId)
                .orElseThrow(() -> new EntityNotFoundException("Plan not found"));

        if (current.getId().equals(target.getId())) {
            return PlanChangePreviewResponse.builder()
                    .changeType("SAME")
                    .currentPlanId(current.getId())
                    .currentPlanName(current.getName())
                    .targetPlanId(target.getId())
                    .targetPlanName(target.getName())
                    .proratedAmountSatang(null)
                    .scheduledEffectiveAt(null)
                    .description("Already on this plan")
                    .build();
        }

        int cmp = Integer.compare(current.getPriceSatang(), target.getPriceSatang());
        if (cmp == 0) {
            throw new InvalidPlanChangeException("Target plan has the same price; use a different plan or contact support");
        }

        if (cmp < 0) {
            long prorated = computeProratedUpgradeSatang(sub, current.getPriceSatang(), target.getPriceSatang());
            long toCharge = Math.max(prorated, prorated > 0 ? MIN_UPGRADE_CHARGE_SATANG : 0);
            return PlanChangePreviewResponse.builder()
                    .changeType("UPGRADE")
                    .currentPlanId(current.getId())
                    .currentPlanName(current.getName())
                    .targetPlanId(target.getId())
                    .targetPlanName(target.getName())
                    .proratedAmountSatang((int) toCharge)
                    .scheduledEffectiveAt(null)
                    .description("Prorated charge for remaining time in the current billing period (minimum may apply).")
                    .build();
        }

        return PlanChangePreviewResponse.builder()
                .changeType("DOWNGRADE")
                .currentPlanId(current.getId())
                .currentPlanName(current.getName())
                .targetPlanId(target.getId())
                .targetPlanName(target.getName())
                .proratedAmountSatang(null)
                .scheduledEffectiveAt(sub.getCurrentPeriodEnd())
                .description("No charge now. Plan switches at the end of the current period.")
                .build();
    }

    @Transactional
    public void scheduleDowngrade(UUID userId, UUID targetPlanId) {
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
            throw new InvalidPlanChangeException("Use downgrade scheduling for same or lower priced plans");
        }

        long prorated = computeProratedUpgradeSatang(sub, current.getPriceSatang(), target.getPriceSatang());
        long amount = Math.max(prorated, prorated > 0 ? MIN_UPGRADE_CHARGE_SATANG : 0);
        if (amount <= 0) {
            throw new InvalidPlanChangeException("Upgrade amount is zero");
        }

        return omiseSubscriptionService.executePlanUpgradeCharge(
                userId, sub.getId(), target.getId(), omiseToken, amount);
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

    private static long computeProratedUpgradeSatang(Subscription sub, int oldPriceSatang, int newPriceSatang) {
        long diff = (long) newPriceSatang - (long) oldPriceSatang;
        if (diff <= 0) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now(BANGKOK);
        LocalDateTime start = sub.getCurrentPeriodStart();
        LocalDateTime end = sub.getCurrentPeriodEnd();
        if (start == null || end == null || !end.isAfter(start)) {
            throw new InvalidPlanChangeException("Invalid subscription period");
        }
        long totalMs = Duration.between(start, end).toMillis();
        long remainingMs = Duration.between(now, end).toMillis();
        if (remainingMs <= 0) {
            return 0;
        }
        if (totalMs <= 0) {
            throw new InvalidPlanChangeException("Invalid billing period length");
        }
        double raw = (double) diff * (double) remainingMs / (double) totalMs;
        return (long) Math.ceil(raw);
    }
}

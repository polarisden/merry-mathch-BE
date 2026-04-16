package com.fsd10.merry_match_backend.service;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fsd10.merry_match_backend.entity.Subscription;
import com.fsd10.merry_match_backend.repository.SubscriptionRepository;

import co.omise.models.OmiseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cron: ต่ออายุ (ตัดเงิน Omise ก่อน แล้วค่อยขยายรอบใน DB) หรือหมดอายุเมื่อไม่ auto-renew.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionRenewalService {

    private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionLifecycleService subscriptionLifecycleService;
    private final OmiseSubscriptionService omiseSubscriptionService;

    /**
     * ประมวลผลทุก subscription ที่เลยรอบบิลแล้ว (ACTIVE + current_period_end &lt;= now).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void processDueSubscriptions() {
        LocalDateTime now = LocalDateTime.now(BANGKOK);
        var due = subscriptionRepository.findActiveDueForRenewalOrExpiry(
                Subscription.SubscriptionStatus.ACTIVE, now);
        for (Subscription s : due) {
            try {
                processSubscriptionRenewal(s.getId());
            } catch (Exception e) {
                log.error("Renewal job failed for subscription {}", s.getId(), e);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSubscriptionRenewal(java.util.UUID subscriptionId) throws OmiseException, java.io.IOException {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow();
        LocalDateTime now = LocalDateTime.now(BANGKOK);
        if (sub.getStatus() != Subscription.SubscriptionStatus.ACTIVE) {
            return;
        }
        if (sub.getCurrentPeriodEnd().isAfter(now)) {
            return;
        }

        if (!Boolean.TRUE.equals(sub.getAutoRenew())) {
            expireSubscription(sub);
            subscriptionRepository.save(sub);
            return;
        }

        if (sub.getOmiseCustomerId() == null || sub.getOmiseCustomerId().isBlank()) {
            log.warn("Cannot renew subscription {}: missing Omise customer", sub.getId());
            expireSubscription(sub);
            subscriptionRepository.save(sub);
            return;
        }

        if (subscriptionLifecycleService.applyPendingPlanChangeIfDue(sub)) {
            subscriptionRepository.save(sub);
        }
        sub = subscriptionRepository.findById(subscriptionId).orElseThrow();

        omiseSubscriptionService.chargeRenewalAndFulfill(sub);
    }

    private static void expireSubscription(Subscription sub) {
        sub.setStatus(Subscription.SubscriptionStatus.EXPIRED);
        sub.setPendingPlan(null);
        sub.setScheduledPlanChangeAt(null);
    }
}

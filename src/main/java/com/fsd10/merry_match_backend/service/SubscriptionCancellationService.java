package com.fsd10.merry_match_backend.service;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fsd10.merry_match_backend.entity.Subscription;
import com.fsd10.merry_match_backend.exception.InvalidPlanChangeException;
import com.fsd10.merry_match_backend.repository.SubscriptionRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * ยกเลิกการต่ออายุอัตโนมัติ — ใช้สิทธิ์แพ็กจนสิ้นรอบบิลปัจจุบัน (cancel_at = current_period_end).
 */
@Service
@RequiredArgsConstructor
public class SubscriptionCancellationService {

    private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");

    private final SubscriptionRepository subscriptionRepository;

    @Transactional
    public void cancelAtPeriodEnd(java.util.UUID userId) {
        Subscription sub = subscriptionRepository.findByUser_Id(userId)
                .orElseThrow(() -> new EntityNotFoundException("No subscription"));
        if (sub.getStatus() != Subscription.SubscriptionStatus.ACTIVE) {
            throw new InvalidPlanChangeException("No active subscription to cancel");
        }
        LocalDateTime now = LocalDateTime.now(BANGKOK);
        if (sub.getCurrentPeriodEnd() != null && !sub.getCurrentPeriodEnd().isAfter(now)) {
            throw new InvalidPlanChangeException("Current period has already ended");
        }
        sub.setAutoRenew(false);
        sub.setCancelAt(sub.getCurrentPeriodEnd());
        sub.setCancelledAt(LocalDateTime.now(BANGKOK));
        subscriptionRepository.save(sub);
    }

    /** ยกเลิกคำขอหยุดต่ออายุ (ก่อนสิ้นรอบ) */
    @Transactional
    public void resumeAutoRenew(java.util.UUID userId) {
        Subscription sub = subscriptionRepository.findByUser_Id(userId)
                .orElseThrow(() -> new EntityNotFoundException("No subscription"));
        if (sub.getStatus() != Subscription.SubscriptionStatus.ACTIVE) {
            throw new InvalidPlanChangeException("No active subscription");
        }
        LocalDateTime now = LocalDateTime.now(BANGKOK);
        if (sub.getCurrentPeriodEnd() != null && !sub.getCurrentPeriodEnd().isAfter(now)) {
            throw new InvalidPlanChangeException("Period has ended; cannot resume auto-renew");
        }
        sub.setAutoRenew(true);
        sub.setCancelAt(null);
        sub.setCancelledAt(null);
        subscriptionRepository.save(sub);
    }
}

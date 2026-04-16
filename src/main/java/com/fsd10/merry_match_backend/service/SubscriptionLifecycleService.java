package com.fsd10.merry_match_backend.service;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fsd10.merry_match_backend.entity.Subscription;
import com.fsd10.merry_match_backend.repository.SubscriptionRepository;

import lombok.RequiredArgsConstructor;

/**
 * ใช้ย้ายแผนเมื่อถึงเวลา (downgrade ที่เลื่อนไปสิ้นรอบบิล).
 * เรียกจาก lazy read (membership) และจาก cron ก่อนต่ออายุ.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionLifecycleService {

    private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");

    private final SubscriptionRepository subscriptionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyPendingPlanChangesIfDue(java.util.UUID userId) {
        subscriptionRepository.findByUser_Id(userId).ifPresent(sub -> {
            if (applyPendingPlanChangeIfDue(sub)) {
                subscriptionRepository.save(sub);
            }
        });
    }

    /**
     * @return true ถ้ามีการย้ายแผน (ควร save entity ต่อ)
     */
    public boolean applyPendingPlanChangeIfDue(Subscription sub) {
        if (sub.getPendingPlan() == null || sub.getScheduledPlanChangeAt() == null) {
            return false;
        }
        if (sub.getStatus() != Subscription.SubscriptionStatus.ACTIVE) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(BANGKOK);
        if (now.isBefore(sub.getScheduledPlanChangeAt())) {
            return false;
        }
        sub.setPlan(sub.getPendingPlan());
        sub.setPendingPlan(null);
        sub.setScheduledPlanChangeAt(null);
        return true;
    }
}

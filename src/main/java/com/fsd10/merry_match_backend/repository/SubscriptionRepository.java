package com.fsd10.merry_match_backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fsd10.merry_match_backend.entity.Subscription;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByUser_Id(UUID userId);

    Optional<Subscription> findByIdAndUser_Id(UUID id, UUID userId);

    /**
     * ACTIVE ที่เลย current_period_end แล้ว — ต้องต่ออายุหรือหมดอายุ (cron).
     */
    @Query("""
            SELECT DISTINCT s FROM Subscription s
            JOIN FETCH s.plan
            LEFT JOIN FETCH s.pendingPlan
            JOIN FETCH s.user
            WHERE s.status = :active
            AND s.currentPeriodEnd <= :now
            """)
    List<Subscription> findActiveDueForRenewalOrExpiry(
            @Param("active") Subscription.SubscriptionStatus active,
            @Param("now") LocalDateTime now);
}

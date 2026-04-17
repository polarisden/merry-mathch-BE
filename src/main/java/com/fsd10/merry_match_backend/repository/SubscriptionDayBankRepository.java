package com.fsd10.merry_match_backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fsd10.merry_match_backend.entity.SubscriptionDayBank;

public interface SubscriptionDayBankRepository extends JpaRepository<SubscriptionDayBank, UUID> {
    Optional<SubscriptionDayBank> findByUser_IdAndPlan_Id(UUID userId, UUID planId);

    @Query("""
            SELECT b FROM SubscriptionDayBank b
            JOIN FETCH b.plan p
            WHERE b.user.id = :userId AND b.remainingDays > 0
            ORDER BY p.priceSatang DESC, b.updatedAt DESC
            """)
    List<SubscriptionDayBank> findPositiveBanksByUserOrderByPlanPriceDesc(@Param("userId") UUID userId);

    @Query("""
            SELECT b FROM SubscriptionDayBank b
            JOIN FETCH b.plan p
            WHERE b.user.id = :userId AND b.remainingDays > 0 AND p.priceSatang < :maxPriceSatang
            ORDER BY p.priceSatang DESC, b.updatedAt DESC
            """)
    List<SubscriptionDayBank> findPositiveBanksByUserBelowPriceOrderByPlanPriceDesc(
            @Param("userId") UUID userId,
            @Param("maxPriceSatang") int maxPriceSatang);
}

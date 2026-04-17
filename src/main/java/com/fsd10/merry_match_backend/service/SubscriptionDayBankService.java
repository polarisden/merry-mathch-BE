package com.fsd10.merry_match_backend.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fsd10.merry_match_backend.entity.Plans;
import com.fsd10.merry_match_backend.entity.SubscriptionDayBank;
import com.fsd10.merry_match_backend.entity.User;
import com.fsd10.merry_match_backend.repository.SubscriptionDayBankRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubscriptionDayBankService {

    private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");

    private final SubscriptionDayBankRepository subscriptionDayBankRepository;

    @Transactional
    public int addDays(User user, Plans plan, int daysToAdd) {
        if (daysToAdd <= 0) {
            return getRemainingDays(user.getId(), plan.getId());
        }
        SubscriptionDayBank bank = subscriptionDayBankRepository
                .findByUser_IdAndPlan_Id(user.getId(), plan.getId())
                .orElseGet(() -> SubscriptionDayBank.builder()
                        .user(user)
                        .plan(plan)
                        .remainingDays(0)
                        .updatedAt(LocalDateTime.now(BANGKOK))
                        .build());
        bank.setRemainingDays(Math.max(0, bank.getRemainingDays()) + daysToAdd);
        bank.setUpdatedAt(LocalDateTime.now(BANGKOK));
        subscriptionDayBankRepository.save(bank);
        return bank.getRemainingDays();
    }

    @Transactional
    public int consumeAllDays(User user, Plans plan) {
        SubscriptionDayBank bank = subscriptionDayBankRepository
                .findByUser_IdAndPlan_Id(user.getId(), plan.getId())
                .orElse(null);
        if (bank == null) {
            return 0;
        }
        int days = Math.max(0, bank.getRemainingDays());
        bank.setRemainingDays(0);
        bank.setUpdatedAt(LocalDateTime.now(BANGKOK));
        subscriptionDayBankRepository.save(bank);
        return days;
    }

    @Transactional(readOnly = true)
    public int getRemainingDays(java.util.UUID userId, java.util.UUID planId) {
        return subscriptionDayBankRepository
                .findByUser_IdAndPlan_Id(userId, planId)
                .map(SubscriptionDayBank::getRemainingDays)
                .map(days -> Math.max(0, days))
                .orElse(0);
    }

    @Transactional(readOnly = true)
    public Optional<Plans> findHighestPricedBankPlan(java.util.UUID userId) {
        return subscriptionDayBankRepository.findPositiveBanksByUserOrderByPlanPriceDesc(userId)
                .stream()
                .map(SubscriptionDayBank::getPlan)
                .findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<Plans> findHighestPricedBankPlanBelowPrice(java.util.UUID userId, int maxPriceSatangExclusive) {
        return subscriptionDayBankRepository
                .findPositiveBanksByUserBelowPriceOrderByPlanPriceDesc(userId, maxPriceSatangExclusive)
                .stream()
                .map(SubscriptionDayBank::getPlan)
                .findFirst();
    }

    @Transactional(readOnly = true)
    public List<SubscriptionDayBank> listPositiveBanks(java.util.UUID userId) {
        return subscriptionDayBankRepository.findPositiveBanksByUserOrderByPlanPriceDesc(userId);
    }
}

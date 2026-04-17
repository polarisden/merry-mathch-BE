package com.fsd10.merry_match_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fsd10.merry_match_backend.entity.Plans;
import com.fsd10.merry_match_backend.entity.SubscriptionDayBank;
import com.fsd10.merry_match_backend.entity.User;
import com.fsd10.merry_match_backend.repository.SubscriptionDayBankRepository;

@ExtendWith(MockitoExtension.class)
class SubscriptionDayBankServiceTest {

    @Mock
    private SubscriptionDayBankRepository subscriptionDayBankRepository;

    @InjectMocks
    private SubscriptionDayBankService subscriptionDayBankService;

    @Test
    void addDays_shouldAccumulateExistingDays() {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        User user = User.builder().id(userId).build();
        Plans plan = Plans.builder().id(planId).build();
        SubscriptionDayBank existing = SubscriptionDayBank.builder()
                .user(user)
                .plan(plan)
                .remainingDays(4)
                .build();

        when(subscriptionDayBankRepository.findByUser_IdAndPlan_Id(userId, planId))
                .thenReturn(Optional.of(existing));

        int total = subscriptionDayBankService.addDays(user, plan, 3);

        assertEquals(7, total);
        verify(subscriptionDayBankRepository).save(existing);
    }

    @Test
    void consumeAllDays_shouldResetToZeroAndReturnPreviousValue() {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        User user = User.builder().id(userId).build();
        Plans plan = Plans.builder().id(planId).build();
        SubscriptionDayBank existing = SubscriptionDayBank.builder()
                .user(user)
                .plan(plan)
                .remainingDays(9)
                .build();

        when(subscriptionDayBankRepository.findByUser_IdAndPlan_Id(userId, planId))
                .thenReturn(Optional.of(existing));

        int consumed = subscriptionDayBankService.consumeAllDays(user, plan);

        assertEquals(9, consumed);
        assertEquals(0, existing.getRemainingDays());
        verify(subscriptionDayBankRepository).save(existing);
    }

    @Test
    void getRemainingDays_shouldReturnZeroWhenMissing() {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        when(subscriptionDayBankRepository.findByUser_IdAndPlan_Id(userId, planId))
                .thenReturn(Optional.empty());

        int days = subscriptionDayBankService.getRemainingDays(userId, planId);

        assertEquals(0, days);
    }
}

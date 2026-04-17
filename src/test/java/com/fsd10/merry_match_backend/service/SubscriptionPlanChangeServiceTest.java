package com.fsd10.merry_match_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fsd10.merry_match_backend.dto.subscription.PlanChangePreviewResponse;
import com.fsd10.merry_match_backend.dto.subscription.SubscriptionCheckoutResponse;
import com.fsd10.merry_match_backend.entity.Plans;
import com.fsd10.merry_match_backend.entity.Subscription;
import com.fsd10.merry_match_backend.entity.User;
import com.fsd10.merry_match_backend.repository.PlansRepository;
import com.fsd10.merry_match_backend.repository.SubscriptionRepository;

@ExtendWith(MockitoExtension.class)
class SubscriptionPlanChangeServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private PlansRepository plansRepository;
    @Mock
    private SubscriptionLifecycleService subscriptionLifecycleService;
    @Mock
    private OmiseSubscriptionService omiseSubscriptionService;
    @Mock
    private SubscriptionDayBankService subscriptionDayBankService;

    @InjectMocks
    private SubscriptionPlanChangeService subscriptionPlanChangeService;

    @Test
    void confirmUpgrade_shouldChargeFullTargetPrice() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID currentPlanId = UUID.randomUUID();
        UUID targetPlanId = UUID.randomUUID();

        User user = User.builder().id(userId).build();
        Plans currentPlan = Plans.builder().id(currentPlanId).priceSatang(79000).name("Basic").build();
        Plans targetPlan = Plans.builder().id(targetPlanId).priceSatang(149000).name("Premium").build();

        Subscription sub = Subscription.builder()
                .id(subscriptionId)
                .user(user)
                .plan(currentPlan)
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .currentPeriodStart(LocalDateTime.now().minusDays(3))
                .currentPeriodEnd(LocalDateTime.now().plusDays(27))
                .autoRenew(true)
                .build();

        SubscriptionCheckoutResponse fakeResponse = SubscriptionCheckoutResponse.builder()
                .subscriptionId(subscriptionId)
                .status("paid")
                .build();

        when(subscriptionRepository.findByUser_Id(userId)).thenReturn(Optional.of(sub));
        when(plansRepository.findById(targetPlanId)).thenReturn(Optional.of(targetPlan));
        when(omiseSubscriptionService.executePlanUpgradeCharge(
                eq(userId), eq(subscriptionId), eq(targetPlanId), eq("tokn_test_123"), eq(149000L)))
                .thenReturn(fakeResponse);

        SubscriptionCheckoutResponse response =
                subscriptionPlanChangeService.confirmUpgrade(userId, targetPlanId, "tokn_test_123");

        assertNotNull(response);
        assertEquals("paid", response.status());
    }

    @Test
    void preview_shouldExposeImmediateModeAndBankData() {
        UUID userId = UUID.randomUUID();
        UUID currentPlanId = UUID.randomUUID();
        UUID targetPlanId = UUID.randomUUID();

        User user = User.builder().id(userId).build();
        Plans currentPlan = Plans.builder().id(currentPlanId).priceSatang(79000).name("Basic").build();
        Plans targetPlan = Plans.builder().id(targetPlanId).priceSatang(149000).name("Premium").build();

        Subscription sub = Subscription.builder()
                .id(UUID.randomUUID())
                .user(user)
                .plan(currentPlan)
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .currentPeriodStart(LocalDateTime.now().minusDays(5))
                .currentPeriodEnd(LocalDateTime.now().plusDays(10))
                .autoRenew(true)
                .build();

        when(subscriptionRepository.findByUser_Id(userId)).thenReturn(Optional.of(sub));
        when(plansRepository.findById(targetPlanId)).thenReturn(Optional.of(targetPlan));
        when(subscriptionDayBankService.getRemainingDays(userId, targetPlanId)).thenReturn(6);

        PlanChangePreviewResponse preview = subscriptionPlanChangeService.preview(userId, targetPlanId);

        assertEquals("UPGRADE", preview.changeType());
        assertEquals(149000, preview.chargeAmountSatang());
        assertEquals(Boolean.TRUE, preview.immediateEffective());
        assertEquals(6, preview.bankedDaysAvailableOnTargetPlan());
    }

    @Test
    void scheduleDowngrade_shouldSetPendingPlanUntilPeriodEnd() {
        UUID userId = UUID.randomUUID();
        UUID currentPlanId = UUID.randomUUID();
        UUID targetPlanId = UUID.randomUUID();

        User user = User.builder().id(userId).build();
        Plans currentPlan = Plans.builder().id(currentPlanId).priceSatang(149000).name("Premium").build();
        Plans targetPlan = Plans.builder().id(targetPlanId).priceSatang(79000).name("Basic").build();

        Subscription sub = Subscription.builder()
                .id(UUID.randomUUID())
                .user(user)
                .plan(currentPlan)
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .currentPeriodStart(LocalDateTime.now().minusDays(5))
                .currentPeriodEnd(LocalDateTime.now().plusDays(10))
                .autoRenew(true)
                .build();

        when(subscriptionRepository.findByUser_Id(userId)).thenReturn(Optional.of(sub));
        when(plansRepository.findById(targetPlanId)).thenReturn(Optional.of(targetPlan));

        subscriptionPlanChangeService.scheduleDowngrade(userId, targetPlanId);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertEquals(currentPlanId, captor.getValue().getPlan().getId());
        assertEquals(targetPlanId, captor.getValue().getPendingPlan().getId());
        verify(subscriptionDayBankService, never()).addDays(any(), any(), any(Integer.class));
    }
}

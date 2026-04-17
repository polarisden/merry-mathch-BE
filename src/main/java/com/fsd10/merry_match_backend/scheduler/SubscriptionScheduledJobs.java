package com.fsd10.merry_match_backend.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fsd10.merry_match_backend.service.SubscriptionRenewalService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Hybrid: รันตาม cron + lazy apply ใน {@code SubscriptionReadService}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "scheduler.subscription.enabled", havingValue = "true", matchIfMissing = true)
public class SubscriptionScheduledJobs {

    private final SubscriptionRenewalService subscriptionRenewalService;

    // Default: hourly. Can be overridden via scheduler.subscription.cron
    @Scheduled(cron = "${scheduler.subscription.cron:0 0 * * * *}")
    public void renewSubscriptions() {
        log.debug("Running subscription renewal / expiry job");
        subscriptionRenewalService.processDueSubscriptions();
    }
}

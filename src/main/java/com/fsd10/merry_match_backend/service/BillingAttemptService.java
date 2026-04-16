package com.fsd10.merry_match_backend.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fsd10.merry_match_backend.entity.BillingRecord;
import com.fsd10.merry_match_backend.entity.Plans;
import com.fsd10.merry_match_backend.entity.Subscription;
import com.fsd10.merry_match_backend.entity.User;
import com.fsd10.merry_match_backend.repository.BillingRecordRepository;

import lombok.RequiredArgsConstructor;

/**
 * Billing persistence: use {@link #upsertAttemptIsolated} when the row must survive caller rollback
 * (FAILED / PENDING before subscription exists). Use {@link #upsertAttemptJoinCaller} when referencing
 * a {@link Subscription} row created in the same transaction (e.g. first subscribe PAID) so FK is visible.
 */
@Service
@RequiredArgsConstructor
public class BillingAttemptService {

    private final BillingRecordRepository billingRecordRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsertAttemptIsolated(
            String omiseChargeId,
            BillingRecord.BillingStatus status,
            User user,
            Subscription subscription,
            Plans plan,
            int amountSatang,
            LocalDateTime periodStart,
            LocalDateTime periodEnd,
            LocalDateTime paidAt,
            String failureCode,
            String failureMessage
    ) {
        applyUpsert(omiseChargeId, status, user, subscription, plan, amountSatang, periodStart, periodEnd, paidAt,
                failureCode, failureMessage);
    }

    /** Joins the caller's transaction (subscription insert must be visible to FK check). */
    @Transactional(propagation = Propagation.REQUIRED)
    public void upsertAttemptJoinCaller(
            String omiseChargeId,
            BillingRecord.BillingStatus status,
            User user,
            Subscription subscription,
            Plans plan,
            int amountSatang,
            LocalDateTime periodStart,
            LocalDateTime periodEnd,
            LocalDateTime paidAt,
            String failureCode,
            String failureMessage
    ) {
        applyUpsert(omiseChargeId, status, user, subscription, plan, amountSatang, periodStart, periodEnd, paidAt,
                failureCode, failureMessage);
    }

    private void applyUpsert(
            String omiseChargeId,
            BillingRecord.BillingStatus status,
            User user,
            Subscription subscription,
            Plans plan,
            int amountSatang,
            LocalDateTime periodStart,
            LocalDateTime periodEnd,
            LocalDateTime paidAt,
            String failureCode,
            String failureMessage
    ) {
        BillingRecord br = billingRecordRepository.findByOmiseChargeId(omiseChargeId).orElse(null);
        if (br == null) {
            br = BillingRecord.builder().build();
            br.setOmiseChargeId(omiseChargeId);
        }
        br.setStatus(status);
        br.setUser(user);
        br.setSubscription(subscription);
        br.setPlan(plan);
        br.setAmountSatang(amountSatang);
        br.setPeriodStart(periodStart);
        br.setPeriodEnd(periodEnd);
        br.setPaidAt(paidAt);
        br.setFailureCode(failureCode);
        br.setFailureMessage(failureMessage);
        billingRecordRepository.save(br);
    }
}


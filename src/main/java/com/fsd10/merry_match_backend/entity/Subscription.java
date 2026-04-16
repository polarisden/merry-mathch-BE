package com.fsd10.merry_match_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "plan_id", nullable = false, columnDefinition = "uuid")
    private UUID planId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "cancel_at")
    private Instant cancelAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "auto_renew", nullable = false)
    private Boolean autoRenew;

    @Column(name = "omise_customer_id")
    private String omiseCustomerId;

    @Column(name = "omise_card_id")
    private String omiseCardId;

    @Column(name = "card_brand")
    private String cardBrand;

    @Column(name = "card_expiration_month")
    private Integer cardExpirationMonth;

    @Column(name = "card_expiration_year")
    private Integer cardExpirationYear;

    @Column(name = "card_last_digits")
    private String cardLastDigits;

    @Column(name = "scheduled_plan_change_at")
    private Instant scheduledPlanChangeAt;

    @Column(name = "pending_plan_id", columnDefinition = "uuid")
    private UUID pendingPlanId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}

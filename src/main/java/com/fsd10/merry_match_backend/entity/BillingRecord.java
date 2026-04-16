package com.fsd10.merry_match_backend.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "billing_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plans plan;

    @Column(name = "amount_satang", nullable = false)
    private Integer amountSatang;

    @Enumerated(EnumType.STRING)
    @JdbcType(BillingStatusPostgreSqlJdbcType.class)
    @Column(name = "status", nullable = false, columnDefinition = "billing_status")
    private BillingStatus status;

    @Column(name = "omise_charge_id", unique = true)
    private String omiseChargeId;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", columnDefinition = "text")
    private String failureMessage;

    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDateTime periodEnd;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // managed by database default (CURRENT_TIMESTAMP)
    @Column(name = "created_at", updatable = false, insertable = false)
    private LocalDateTime createdAt;

    public enum BillingStatus {
        PAID, PENDING, FAILED
    }
}

/**
 * ก่อนใช้ Entity นี้ต้องรันคำสั่งต่อไปนี้ใน Supabase ก่อน
 *
 * 1. สร้าง ENUM type:
 *    CREATE TYPE billing_status AS ENUM ('PAID', 'PENDING', 'FAILED');
 *
 * 2. สร้าง table:
 *    CREATE TABLE public.billing_records (
 *      id              uuid NOT NULL DEFAULT gen_random_uuid(),
 *      subscription_id uuid NULL REFERENCES public.subscriptions(id),
 *      user_id         uuid NOT NULL REFERENCES public.users(id),
 *      plan_id         uuid NOT NULL REFERENCES public.plans(id),
 *      amount_satang   int NOT NULL,
 *      status          billing_status NOT NULL,
 *      omise_charge_id varchar(255) NULL UNIQUE,
 *      failure_code    varchar(100) NULL,
 *      failure_message text NULL,
 *      period_start    timestamp NOT NULL,
 *      period_end      timestamp NOT NULL,
 *      paid_at         timestamp NULL,
 *      created_at      timestamp NULL DEFAULT CURRENT_TIMESTAMP,
 *      CONSTRAINT billing_records_pkey PRIMARY KEY (id)
 *    );
 */
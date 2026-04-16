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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "subscriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plans plan;

    /** แผนที่จะเปลี่ยนเมื่อถึงรอบบิล (downgrade ที่เลื่อนไปเดือนถัดไป) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pending_plan_id")
    private Plans pendingPlan;

    /** เมื่อถึงเวลานี้ (เช่น สิ้นรอบปัจจุบัน) จะย้าย plan → pending_plan แล้วล้าง pending */
    @Column(name = "scheduled_plan_change_at")
    private LocalDateTime scheduledPlanChangeAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(name = "current_period_start", nullable = false)
    private LocalDateTime currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private LocalDateTime currentPeriodEnd;

    // วันที่จะหยุดใช้งานจริง (= current_period_end ตอน cancel)
    @Column(name = "cancel_at")
    private LocalDateTime cancelAt;

    // วันที่กด cancel จริงๆ
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Builder.Default
    @Column(name = "auto_renew", nullable = false)
    private Boolean autoRenew = true;

    // cust_xxx จาก Omise
    @Column(name = "omise_customer_id")
    private String omiseCustomerId;

    /** card_xxx จาก Omise — ใช้ charge renewal / sync กับ default card บน customer */
    @Column(name = "omise_card_id", length = 255)
    private String omiseCardId;

    /** Snapshot สำหรับ UI เท่านั้น — จาก Omise Card ตอนชำระสำเร็จ */
    @Column(name = "card_brand", length = 50)
    private String cardBrand;

    @Column(name = "card_last_digits", length = 4)
    private String cardLastDigits;

    @Column(name = "card_expiration_month")
    private Integer cardExpirationMonth;

    @Column(name = "card_expiration_year")
    private Integer cardExpirationYear;

    // managed by database default (CURRENT_TIMESTAMP)
    @Column(name = "created_at", updatable = false, insertable = false)
    private LocalDateTime createdAt;

    // managed by database trigger (update_updated_at_column)
    @Column(name = "updated_at", insertable = false)
    private LocalDateTime updatedAt;

    public enum SubscriptionStatus {
        ACTIVE,
        EXPIRED
    }
}

/**
 * ก่อนใช้ Entity นี้ต้องรันคำสั่งต่อไปนี้ใน Supabase ก่อน เพราะใช้ hibernate
 *
 * 1. สร้าง ENUM type:
 *    CREATE TYPE subscription_status AS ENUM ('ACTIVE', 'EXPIRED');
 *
 * 2. สร้าง table:
 *    CREATE TABLE public.subscriptions (
 *      id                   uuid NOT NULL DEFAULT gen_random_uuid(),
 *      user_id              uuid NOT NULL UNIQUE REFERENCES public.users(id) ON DELETE CASCADE,
 *      plan_id              uuid NOT NULL REFERENCES public.plans(id),
 *      status               subscription_status NOT NULL DEFAULT 'ACTIVE',
 *      current_period_start timestamp NOT NULL,
 *      current_period_end   timestamp NOT NULL,
 *      cancel_at            timestamp NULL,
 *      cancelled_at         timestamp NULL,
 *      auto_renew           boolean NOT NULL DEFAULT true,
 *      omise_customer_id    varchar(255) NULL,
 *      omise_card_id        varchar(255) NULL,
 *      card_brand           varchar(50) NULL,
 *      card_last_digits     varchar(4) NULL,
 *      card_expiration_month smallint NULL,
 *      card_expiration_year  smallint NULL,
 *      created_at           timestamp NULL DEFAULT CURRENT_TIMESTAMP,
 *      updated_at           timestamp NULL,
 *      CONSTRAINT subscriptions_pkey PRIMARY KEY (id)
 *    );
*  create table public.subscriptions (
*   id uuid not null default gen_random_uuid (),
*   user_id uuid not null,
*   plan_id uuid not null,
*   status public.subscription_status not null default 'ACTIVE'::subscription_status,
*   current_period_start timestamp without time zone not null,
*   current_period_end timestamp without time zone not null,
*   cancel_at timestamp without time zone null,
*   cancelled_at timestamp without time zone null,
*   auto_renew boolean not null default true,
*   omise_customer_id character varying(255) null,
*   created_at timestamp without time zone null default CURRENT_TIMESTAMP,
*   updated_at timestamp without time zone null,
*   card_brand character varying(50) null,
*   card_expiration_month integer null,
*   card_expiration_year integer null,
*   card_last_digits character varying(4) null,
*   scheduled_plan_change_at timestamp without time zone null,
*   pending_plan_id uuid null,
*   constraint subscriptions_pkey primary key (id),
*   constraint subscriptions_user_id_key unique (user_id),
*   constraint fkksi8ljpgoqxibh47ab46i533 foreign KEY (pending_plan_id) references plans (id),
*   constraint subscriptions_plan_id_fkey foreign KEY (plan_id) references plans (id),
*   constraint subscriptions_user_id_fkey foreign KEY (user_id) references users (id) on delete CASCADE
* ) TABLESPACE pg_default;
 * 3. สร้าง trigger:
 *    CREATE TRIGGER set_subscriptions_updated_at
 *      BEFORE UPDATE ON subscriptions
 *      FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
 *
 * Omise card id (หลังมีตารางแล้ว):
 *    ALTER TABLE public.subscriptions ADD COLUMN IF NOT EXISTS omise_card_id varchar(255);
 *
 * เพิ่มคอลัมน์แสดงผลบัตร (หลังมีตารางแล้ว):
 *    ALTER TABLE public.subscriptions ADD COLUMN IF NOT EXISTS card_brand varchar(50);
 *    ALTER TABLE public.subscriptions ADD COLUMN IF NOT EXISTS card_last_digits varchar(4);
 *    ALTER TABLE public.subscriptions ADD COLUMN IF NOT EXISTS card_expiration_month smallint;
 *    ALTER TABLE public.subscriptions ADD COLUMN IF NOT EXISTS card_expiration_year smallint;
 *
 * Upgrade / downgrade (scheduled):
 *    ALTER TABLE public.subscriptions ADD COLUMN IF NOT EXISTS pending_plan_id uuid NULL REFERENCES public.plans(id);
 *    ALTER TABLE public.subscriptions ADD COLUMN IF NOT EXISTS scheduled_plan_change_at timestamp NULL;
 */
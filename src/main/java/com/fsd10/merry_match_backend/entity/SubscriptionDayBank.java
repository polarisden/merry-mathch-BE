package com.fsd10.merry_match_backend.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "subscription_day_banks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionDayBank {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "plan_id", nullable = false)
    private Plans plan;

    @Column(name = "remaining_days", nullable = false)
    @Builder.Default
    private Integer remainingDays = 0;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

/**
 * ก่อนใช้ Entity นี้ต้องรัน SQL สร้างตารางก่อน (หรือให้ Flyway รัน migration):
 *
 * CREATE TABLE IF NOT EXISTS public.subscription_day_banks (
 *   id uuid NOT NULL DEFAULT gen_random_uuid(),
 *   user_id uuid NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
 *   plan_id uuid NOT NULL REFERENCES public.plans(id) ON DELETE CASCADE,
 *   remaining_days integer NOT NULL DEFAULT 0,
 *   updated_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
 *   CONSTRAINT subscription_day_banks_pkey PRIMARY KEY (id),
 *   CONSTRAINT subscription_day_banks_user_plan_unique UNIQUE (user_id, plan_id)
 * );
 */

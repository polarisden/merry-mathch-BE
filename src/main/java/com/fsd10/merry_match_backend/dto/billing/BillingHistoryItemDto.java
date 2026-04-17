package com.fsd10.merry_match_backend.dto.billing;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;

/**
 * One line item for billing / payment history UI.
 */
@Builder
public record BillingHistoryItemDto(
        UUID id,
        /** วันที่ชำระ (ใช้ paid_at จาก Omise; ถ้าไม่มีใช้ created_at) */
        LocalDateTime billedAt,
        String planName,
        /** จำนวนเงินเป็นสตางค์ (หาร 100 ได้บาท) */
        Integer amountSatang,
        String status
) {}

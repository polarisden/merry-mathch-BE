package com.fsd10.merry_match_backend.dto.subscription;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;

/**
 * @param changeType SAME | UPGRADE | DOWNGRADE
 * @param proratedAmountSatang จำนวนที่จะเรียกเก็บเพิ่มเมื่อ upgrade (สตางค์); null ถ้าไม่เกี่ยว
 * @param scheduledEffectiveAt วันที่แผน downgrade จะมีผล (สิ้นรอบปัจจุบัน); null ถ้าไม่เกี่ยว
 */
@Builder
public record PlanChangePreviewResponse(
        String changeType,
        UUID currentPlanId,
        String currentPlanName,
        UUID targetPlanId,
        String targetPlanName,
        Integer proratedAmountSatang,
        LocalDateTime scheduledEffectiveAt,
        String description
) {}

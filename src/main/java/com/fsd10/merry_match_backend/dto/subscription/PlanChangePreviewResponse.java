package com.fsd10.merry_match_backend.dto.subscription;

import java.util.UUID;

import lombok.Builder;

/**
 * @param changeType SAME | UPGRADE | DOWNGRADE
 * @param chargeAmountSatang จำนวนที่จะเรียกเก็บตอนกดยืนยัน (upgrade = ราคาเต็ม plan ใหม่)
 * @param immediateEffective true เมื่อเปลี่ยนแผนทันที
 * @param bankedDaysFromCurrentPlan จำนวนวันที่จะถูกเก็บจาก plan ปัจจุบันถ้ากดยืนยัน
 * @param bankedDaysAvailableOnTargetPlan จำนวนวันที่สะสมไว้ของ plan เป้าหมาย
 */
@Builder
public record PlanChangePreviewResponse(
        String changeType,
        UUID currentPlanId,
        String currentPlanName,
        UUID targetPlanId,
        String targetPlanName,
        Integer chargeAmountSatang,
        Boolean immediateEffective,
        Integer bankedDaysFromCurrentPlan,
        Integer bankedDaysAvailableOnTargetPlan,
        String description
) {}

package com.fsd10.merry_match_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fsd10.merry_match_backend.auth.SupabaseJwtService;
import com.fsd10.merry_match_backend.service.SubscriptionCancellationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionCancellationController {

    private final SubscriptionCancellationService subscriptionCancellationService;
    private final SupabaseJwtService supabaseJwtService;

    /**
     * ไม่ต่ออายุอัตโนมัติ — ใช้แพ็กจนสิ้นรอบ (auto_renew=false, cancel_at=current_period_end).
     */
    @PostMapping("/cancel")
    public ResponseEntity<Void> cancelAtPeriodEnd(@RequestHeader("Authorization") String authorization) {
        var userId = supabaseJwtService.requireUserIdFromAuthorization(authorization);
        subscriptionCancellationService.cancelAtPeriodEnd(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * เปิดกลับ auto-renew หลังกดยกเลิก (ก่อนสิ้นรอบบิล).
     */
    @PostMapping("/resume")
    public ResponseEntity<Void> resumeAutoRenew(@RequestHeader("Authorization") String authorization) {
        var userId = supabaseJwtService.requireUserIdFromAuthorization(authorization);
        subscriptionCancellationService.resumeAutoRenew(userId);
        return ResponseEntity.noContent().build();
    }
}

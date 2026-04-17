package com.fsd10.merry_match_backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fsd10.merry_match_backend.auth.SupabaseJwtService;
import com.fsd10.merry_match_backend.dto.billing.BillingHistoryItemDto;
import com.fsd10.merry_match_backend.service.BillingReadService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingReadService billingReadService;
    private final SupabaseJwtService supabaseJwtService;

    /**
     * ประวัติการเรียกเก็บเงินของผู้ใช้ปัจจุบัน (เรียงใหม่สุดก่อน).
     * ไม่มีรายการ → 200 และ body เป็น []
     */
    @GetMapping("/history")
    public ResponseEntity<List<BillingHistoryItemDto>> listHistory(
            @RequestHeader("Authorization") String authorization
    ) {
        UUID userId = supabaseJwtService.requireUserIdFromAuthorization(authorization);
        return ResponseEntity.ok(billingReadService.listHistoryForUser(userId));
    }
}

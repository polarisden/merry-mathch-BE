package com.fsd10.merry_match_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fsd10.merry_match_backend.auth.SupabaseJwtService;
import com.fsd10.merry_match_backend.dto.subscription.PlanChangePreviewRequest;
import com.fsd10.merry_match_backend.dto.subscription.PlanChangePreviewResponse;
import com.fsd10.merry_match_backend.dto.subscription.PlanChangeUpgradeRequest;
import com.fsd10.merry_match_backend.dto.subscription.SubscriptionCheckoutResponse;
import com.fsd10.merry_match_backend.service.SubscriptionPlanChangeService;

import co.omise.models.OmiseException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.UUID;

@RestController
@RequestMapping("/api/subscriptions/plan-change")
@RequiredArgsConstructor
public class SubscriptionPlanChangeController {

    private final SubscriptionPlanChangeService subscriptionPlanChangeService;
    private final SupabaseJwtService supabaseJwtService;

    /** Preview: upgrade is immediate/full-charge, downgrade is scheduled at period end. */
    @PostMapping("/preview")
    public ResponseEntity<PlanChangePreviewResponse> preview(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody PlanChangePreviewRequest request
    ) {
        var userId = supabaseJwtService.requireUserIdFromAuthorization(authorization);
        return ResponseEntity.ok(subscriptionPlanChangeService.preview(userId, request.planId()));
    }

    /** Downgrade: no charge now, apply at current period end (pending). */
    @PostMapping("/downgrade")
    public ResponseEntity<Void> scheduleDowngrade(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody PlanChangePreviewRequest request
    ) {
        var userId = supabaseJwtService.requireUserIdFromAuthorization(authorization);
        subscriptionPlanChangeService.scheduleDowngrade(userId, request.planId());
        return ResponseEntity.accepted().build();
    }

    /** Cancel previously scheduled downgrade and keep current plan. */
    @PostMapping("/downgrade/cancel")
    public ResponseEntity<Void> cancelScheduledDowngrade(
            @RequestHeader("Authorization") String authorization
    ) {
        var userId = supabaseJwtService.requireUserIdFromAuthorization(authorization);
        subscriptionPlanChangeService.cancelScheduledDowngrade(userId);
        return ResponseEntity.noContent().build();
    }

    /** Upgrade: switch immediately and charge full target plan price via Omise. */
    @PostMapping("/upgrade")
    public ResponseEntity<SubscriptionCheckoutResponse> confirmUpgrade(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody PlanChangeUpgradeRequest request
    ) throws OmiseException, java.io.IOException {
        UUID userId = supabaseJwtService.requireUserIdFromAuthorization(authorization);
        return ResponseEntity.ok(
                subscriptionPlanChangeService.confirmUpgrade(userId, request.planId(), request.omiseToken()));
    }
}

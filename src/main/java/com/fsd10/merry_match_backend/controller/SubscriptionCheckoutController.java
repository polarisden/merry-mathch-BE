package com.fsd10.merry_match_backend.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fsd10.merry_match_backend.auth.SupabaseJwtService;
import com.fsd10.merry_match_backend.dto.subscription.SubscriptionCheckoutRequest;
import com.fsd10.merry_match_backend.dto.subscription.SubscriptionCheckoutResponse;
import com.fsd10.merry_match_backend.service.OmiseSubscriptionService;

import co.omise.models.OmiseException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionCheckoutController {

    private final OmiseSubscriptionService omiseSubscriptionService;
    private final SupabaseJwtService supabaseJwtService;

    /**
     * Subscribe with a one-time Omise card token ({@code tokn_...}) from Omise.js.
     * Amount is taken from {@link com.fsd10.merry_match_backend.entity.Plans#getPriceSatang()} only (never from client).
     */
    @PostMapping("/checkout")
    public ResponseEntity<SubscriptionCheckoutResponse> checkout(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody SubscriptionCheckoutRequest request
    ) throws OmiseException, java.io.IOException {
        UUID userId = supabaseJwtService.requireUserIdFromAuthorization(authorization);
        return ResponseEntity.ok(omiseSubscriptionService.checkout(userId, request));
    }
}

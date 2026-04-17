package com.fsd10.merry_match_backend.controller;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fsd10.merry_match_backend.auth.SupabaseJwtService;
import com.fsd10.merry_match_backend.dto.subscription.PaymentCardDto;
import com.fsd10.merry_match_backend.dto.subscription.UpdatePaymentCardRequest;
import com.fsd10.merry_match_backend.service.OmiseSubscriptionService;

import co.omise.models.OmiseException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionPaymentMethodController {

    private final OmiseSubscriptionService omiseSubscriptionService;
    private final SupabaseJwtService supabaseJwtService;

    /**
     * Replace default card on the Omise customer (no charge). Visa/Mastercard only.
     */
    @PostMapping("/payment-method")
    public ResponseEntity<PaymentCardDto> updatePaymentMethod(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody UpdatePaymentCardRequest request
    ) throws OmiseException, IOException {
        UUID userId = supabaseJwtService.requireUserIdFromAuthorization(authorization);
        return ResponseEntity.ok(omiseSubscriptionService.updatePaymentMethod(userId, request.omiseToken()));
    }
}

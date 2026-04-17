package com.fsd10.merry_match_backend.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fsd10.merry_match_backend.auth.SupabaseJwtService;
import com.fsd10.merry_match_backend.dto.subscription.SubscriptionDetailDto;
import com.fsd10.merry_match_backend.service.SubscriptionReadService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionReadService subscriptionReadService;
    private final SupabaseJwtService supabaseJwtService;

    /**
     * Current user's subscription by id (must belong to JWT user). For success page / account.
     */
    @GetMapping("/{id}")
    public ResponseEntity<SubscriptionDetailDto> getSubscription(
            @RequestHeader("Authorization") String authorization,
            @PathVariable UUID id
    ) {
        UUID userId = supabaseJwtService.requireUserIdFromAuthorization(authorization);
        return ResponseEntity.ok(subscriptionReadService.getSubscriptionForUser(id, userId));
    }
}

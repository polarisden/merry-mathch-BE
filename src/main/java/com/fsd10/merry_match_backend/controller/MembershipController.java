package com.fsd10.merry_match_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fsd10.merry_match_backend.auth.SupabaseJwtService;
import com.fsd10.merry_match_backend.dto.subscription.SubscriptionDetailDto;
import com.fsd10.merry_match_backend.service.SubscriptionReadService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/membership")
@RequiredArgsConstructor
public class MembershipController {

    private final SubscriptionReadService subscriptionReadService;
    private final SupabaseJwtService supabaseJwtService;

    /**
     * Current user's subscription (one row per user). 404 if no subscription yet.
     */
    @GetMapping("/current")
    public ResponseEntity<SubscriptionDetailDto> getCurrentMembership(
            @RequestHeader("Authorization") String authorization
    ) {
        var userId = supabaseJwtService.requireUserIdFromAuthorization(authorization);
        return subscriptionReadService.findCurrentMembershipForUser(userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

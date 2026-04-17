package com.fsd10.merry_match_backend.controller;

import com.fsd10.merry_match_backend.dto.SwipeHistoryResponse;
import com.fsd10.merry_match_backend.dto.SwipeRequest;
import com.fsd10.merry_match_backend.entity.Swipe;
import com.fsd10.merry_match_backend.service.SwipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SwipeController {

    private final SwipeService swipeService;

    @PostMapping("/swipes")
    public ResponseEntity<Swipe> recordSwipe(@RequestBody SwipeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(swipeService.recordSwipe(req));
    }

    @GetMapping("/swipes/{userId}/history")
    public ResponseEntity<SwipeHistoryResponse> getSwipeHistory(@PathVariable UUID userId) {
        return ResponseEntity.ok(swipeService.getSwipeHistory(userId));
    }
}

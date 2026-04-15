package com.fsd10.merry_match_backend.controller;

import com.fsd10.merry_match_backend.dto.MatchResponse;
import com.fsd10.merry_match_backend.service.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    @GetMapping("/matchList")
    public ResponseEntity<List<MatchResponse>> getMatchList(@RequestParam UUID userId) {
        return ResponseEntity.ok(matchService.getMatchesByUserId(userId));
    }
}

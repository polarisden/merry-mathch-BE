package com.fsd10.merry_match_backend.controller;

import com.fsd10.merry_match_backend.dto.IncrementMerryRequest;
import com.fsd10.merry_match_backend.service.MerryCountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MerryCountController {

    private final MerryCountService merryCountService;

    @PostMapping("/count/merry")
    public ResponseEntity<Void> incrementMerry(@RequestBody IncrementMerryRequest req) {
        merryCountService.incrementMerryCount(req.userId());
        return ResponseEntity.ok().build();
    }
}

package com.fsd10.merry_match_backend.controller;

import com.fsd10.merry_match_backend.dto.CreateInterestRequest;
import com.fsd10.merry_match_backend.entity.Interest;
import com.fsd10.merry_match_backend.service.InterestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/interests")
@RequiredArgsConstructor
public class InterestController {

  private final InterestService interestService;

  @GetMapping
  public ResponseEntity<List<Interest>> getAllInterests() {
    return ResponseEntity.ok(interestService.getAllInterests());
  }

  @PostMapping
  public ResponseEntity<Interest> createInterest(@Valid @RequestBody CreateInterestRequest request) {
    Interest saved = interestService.createOrGet(request.name());
    return ResponseEntity.status(HttpStatus.CREATED).body(saved);
  }
}

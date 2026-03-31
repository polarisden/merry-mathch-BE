package com.fsd10.merry_match_backend.controller;

import com.fsd10.merry_match_backend.entity.Interest;
import com.fsd10.merry_match_backend.service.InterestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
}

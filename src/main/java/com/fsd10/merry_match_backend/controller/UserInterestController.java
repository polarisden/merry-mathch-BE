package com.fsd10.merry_match_backend.controller;

import com.fsd10.merry_match_backend.dto.UserInterestsRequest;
import com.fsd10.merry_match_backend.dto.UserInterestsResponse;
import com.fsd10.merry_match_backend.service.ProfileImageService;
import com.fsd10.merry_match_backend.service.UserInterestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserInterestController {

  private final UserInterestService userInterestService;
  private final ProfileImageService profileImageService;

  @PutMapping("/users/me/interests")
  public ResponseEntity<UserInterestsResponse> replaceMyInterests(
      @RequestHeader(name = "Authorization", required = false) String authorization,
      @Valid @RequestBody UserInterestsRequest request
  ) {
    if (authorization == null || authorization.isBlank()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    try {
      UUID userId = profileImageService.extractUserIdFromJwt(authorization);
      UserInterestsResponse response = userInterestService.replaceUserInterests(userId, request.getInterestIds());
      return ResponseEntity.ok(response);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
  }
}

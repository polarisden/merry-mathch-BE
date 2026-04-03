package com.fsd10.merry_match_backend.controller;

import com.fsd10.merry_match_backend.service.AuthService;
import com.fsd10.merry_match_backend.service.ProfileImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserAccountController {

  private final AuthService authService;
  private final ProfileImageService profileImageService;

  @DeleteMapping("/users/me")
  public ResponseEntity<Void> deleteMyAccount(
      @RequestHeader(name = "Authorization", required = false) String authorization
  ) {
    if (authorization == null || authorization.isBlank()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    try {
      UUID userId = profileImageService.extractUserIdFromJwt(authorization);
      authService.deleteAccount(userId);
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
  }
}


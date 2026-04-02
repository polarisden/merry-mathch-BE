package com.fsd10.merry_match_backend.controller;

import com.fsd10.merry_match_backend.dto.ProfileImageUploadResponse;
import com.fsd10.merry_match_backend.service.ProfileImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProfileImageController {

  private final ProfileImageService profileImageService;

  @PostMapping("/users/me/profile-images")
  public ResponseEntity<ProfileImageUploadResponse> upload(
      @RequestHeader(name = "Authorization", required = false) String authorization,
      @RequestParam("file") MultipartFile file,
      @RequestParam(name = "isPrimary", required = false, defaultValue = "false") boolean isPrimary
  ) {
    if (authorization == null || authorization.isBlank()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    try {
      UUID userId = profileImageService.extractUserIdFromJwt(authorization);
      ProfileImageUploadResponse response = profileImageService.uploadForUser(userId, file, isPrimary);
      return ResponseEntity.status(HttpStatus.CREATED).body(response);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
  }

  @GetMapping("/users/me/profile-images")
  public ResponseEntity<List<ProfileImageUploadResponse>> listMyImages(
      @RequestHeader(name = "Authorization", required = false) String authorization
  ) {
    if (authorization == null || authorization.isBlank()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    try {
      UUID userId = profileImageService.extractUserIdFromJwt(authorization);
      return ResponseEntity.ok(profileImageService.listForUser(userId));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
  }

  @DeleteMapping("/users/me/profile-images/{imageId}")
  public ResponseEntity<Void> deleteMyImage(
      @RequestHeader(name = "Authorization", required = false) String authorization,
      @PathVariable UUID imageId
  ) {
    if (authorization == null || authorization.isBlank()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    try {
      UUID userId = profileImageService.extractUserIdFromJwt(authorization);
      profileImageService.deleteForUser(userId, imageId);
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    } catch (jakarta.persistence.EntityNotFoundException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
  }
}


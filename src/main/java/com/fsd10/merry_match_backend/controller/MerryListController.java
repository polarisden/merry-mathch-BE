package com.fsd10.merry_match_backend.controller;

import com.fsd10.merry_match_backend.auth.SupabaseJwtService;
import com.fsd10.merry_match_backend.dto.MerryActionResponse;
import com.fsd10.merry_match_backend.dto.MerryListResponse;
import com.fsd10.merry_match_backend.service.MerryListService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class MerryListController {

  private final MerryListService merryListService;
  private final SupabaseJwtService supabaseJwtService;

  @GetMapping("/merry-list")
  public ResponseEntity<MerryListResponse> getMerryList(
      @RequestHeader(name = "Authorization", required = false) String authorization,
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
  ) {
    UUID currentUserId = requireUser(authorization);
    return ResponseEntity.ok(merryListService.getMerryList(currentUserId, page, limit));
  }

  @PostMapping("/merry/{targetProfileId}")
  public ResponseEntity<MerryActionResponse> merry(
      @RequestHeader(name = "Authorization", required = false) String authorization,
      @PathVariable UUID targetProfileId
  ) {
    UUID currentUserId = requireUser(authorization);
    return ResponseEntity.status(HttpStatus.CREATED).body(merryListService.merry(currentUserId, targetProfileId));
  }

  @DeleteMapping("/merry-list/{targetProfileId}")
  public ResponseEntity<Void> removeFromMerryList(
      @RequestHeader(name = "Authorization", required = false) String authorization,
      @PathVariable UUID targetProfileId
  ) {
    UUID currentUserId = requireUser(authorization);
    merryListService.removeMerryFromList(currentUserId, targetProfileId);
    return ResponseEntity.noContent().build();
  }

  private UUID requireUser(String authorization) {
    if (authorization == null || authorization.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    try {
      return supabaseJwtService.requireUserIdFromAuthorization(authorization);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    }
  }
}

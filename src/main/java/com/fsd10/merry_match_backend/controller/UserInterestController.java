package com.fsd10.merry_match_backend.controller;

import com.fsd10.merry_match_backend.dto.UserInterestsResponse;
import com.fsd10.merry_match_backend.auth.SupabaseJwtService;
import com.fsd10.merry_match_backend.service.UserInterestService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserInterestController {

  private final UserInterestService userInterestService;
  private final SupabaseJwtService supabaseJwtService;

  @PutMapping("/users/me/interests")
  public ResponseEntity<UserInterestsResponse> replaceMyInterests(
      @RequestHeader(name = "Authorization", required = false) String authorization,
      @RequestBody JsonNode body
  ) {
    if (authorization == null || authorization.isBlank()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    try {
      List<UUID> interestIds = parseInterestIdsFromBody(body);
      UUID userId = supabaseJwtService.requireUserIdFromAuthorization(authorization);
      UserInterestsResponse response = userInterestService.replaceUserInterests(userId, interestIds);
      return ResponseEntity.ok(response);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
  }

  /**
   * Accepts {@code {"interestIds":["uuid",...]}} or a raw JSON array {@code ["uuid",...]}.
   */
  private static List<UUID> parseInterestIdsFromBody(JsonNode body) {
    if (body == null || body.isNull()) {
      throw new IllegalArgumentException("Body required");
    }
    JsonNode arr;
    if (body.isArray()) {
      arr = body;
    } else if (body.isObject()) {
      arr = body.get("interestIds");
      if (arr == null || !arr.isArray()) {
        throw new IllegalArgumentException("Expected \"interestIds\" array or a raw array of UUID strings");
      }
    } else {
      throw new IllegalArgumentException("Expected JSON object or array");
    }
    List<UUID> out = new ArrayList<>();
    for (JsonNode n : arr) {
      if (n == null || n.isNull()) {
        continue;
      }
      if (!n.isString()) {
        throw new IllegalArgumentException("Each interest id must be a UUID string");
      }
      String s = n.asString();
      if (!s.isBlank()) {
        out.add(UUID.fromString(s));
      }
    }
    return out;
  }
}

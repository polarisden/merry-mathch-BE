package com.fsd10.merry_match_backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.fsd10.merry_match_backend.dto.UpdateUserProfileRequest;
import com.fsd10.merry_match_backend.dto.UserDataResponse;
import com.fsd10.merry_match_backend.dto.UserProfileResponse;
import com.fsd10.merry_match_backend.auth.SupabaseJwtService;
import com.fsd10.merry_match_backend.service.UserProfileService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserProfileController {

	private final UserProfileService userProfileService;
	private final SupabaseJwtService supabaseJwtService;

	@GetMapping("/users")
	public ResponseEntity<List<UserDataResponse>> getAllUserData() {
		return ResponseEntity.ok(userProfileService.getAllUserData());
	}

	@GetMapping("/users/{userId}/profile")
	public ResponseEntity<UserProfileResponse> getProfile(@PathVariable UUID userId) {
		return ResponseEntity.ok(userProfileService.getProfile(userId));
	}

	@PutMapping("/users/{userId}/profile")
	public ResponseEntity<UserProfileResponse> updateProfile(
			@PathVariable UUID userId,
			@RequestBody UpdateUserProfileRequest req
	) {
		return ResponseEntity.ok(userProfileService.updateProfile(userId, req));
	}

	@GetMapping("/users/me/profile")
	public ResponseEntity<UserProfileResponse> getMyProfile(
			@RequestHeader(name = "Authorization", required = false) String authorization
	) {
		if (authorization == null || authorization.isBlank()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		try {
			UUID userId = supabaseJwtService.requireUserIdFromAuthorization(authorization);
			return ResponseEntity.ok(userProfileService.getProfile(userId));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
		}
	}

	@PutMapping("/users/me/profile")
	public ResponseEntity<UserProfileResponse> updateMyProfile(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@RequestBody UpdateUserProfileRequest req
	) {
		if (authorization == null || authorization.isBlank()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		try {
			UUID userId = supabaseJwtService.requireUserIdFromAuthorization(authorization);
			return ResponseEntity.ok(userProfileService.updateProfile(userId, req));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
		}
	}
}


package com.fsd10.merry_match_backend.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fsd10.merry_match_backend.dto.UpdateUserProfileRequest;
import com.fsd10.merry_match_backend.dto.UserProfileResponse;
import com.fsd10.merry_match_backend.service.UserProfileService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserProfileController {

	private final UserProfileService userProfileService;

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
}


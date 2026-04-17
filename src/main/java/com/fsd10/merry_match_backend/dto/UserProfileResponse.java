package com.fsd10.merry_match_backend.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record UserProfileResponse(
		UUID id,
		String email,
		String username,
		String name,
		LocalDate dateOfBirth,
		String gender,
		String sexualPreference,
		String racialPreference,
		String meetingInterest,
		String locationCountry,
		String locationCity,
		String bio,
		String role,
		Instant createdAt,
		Instant updatedAt,
		List<InterestResponse> interests,
		Integer merryCount,
		List<ProfileImageUploadResponse> images
) {}


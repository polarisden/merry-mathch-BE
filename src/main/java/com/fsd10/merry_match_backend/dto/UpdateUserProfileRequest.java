package com.fsd10.merry_match_backend.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record UpdateUserProfileRequest(
		// email intentionally excluded (read-only)
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
		List<UUID> interestIds
) {}


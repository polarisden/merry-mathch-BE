package com.fsd10.merry_match_backend.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAlias;

public record UpdateUserProfileRequest(
		// email intentionally excluded (read-only)
		String username,
		String name,
		LocalDate dateOfBirth,
		@JsonAlias("sexualIdentity") String gender,
		String sexualPreference,
		String racialPreference,
		String meetingInterest,
		@JsonAlias("location") String locationCountry,
		@JsonAlias("city") String locationCity,
		String bio,
		List<UUID> interestIds
) {}


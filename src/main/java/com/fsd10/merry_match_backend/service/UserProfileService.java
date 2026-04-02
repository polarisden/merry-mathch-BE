package com.fsd10.merry_match_backend.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fsd10.merry_match_backend.dto.InterestResponse;
import com.fsd10.merry_match_backend.dto.UpdateUserProfileRequest;
import com.fsd10.merry_match_backend.dto.UserProfileResponse;
import com.fsd10.merry_match_backend.entity.Interest;
import com.fsd10.merry_match_backend.entity.User;
import com.fsd10.merry_match_backend.entity.UserInterest;
import com.fsd10.merry_match_backend.repository.InterestRepository;
import com.fsd10.merry_match_backend.repository.UserInterestRepository;
import com.fsd10.merry_match_backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserProfileService {

	private final UserRepository userRepository;
	private final InterestRepository interestRepository;
	private final UserInterestRepository userInterestRepository;

	@Transactional(readOnly = true)
	public UserProfileResponse getProfile(UUID userId) {
		var user = userRepository.findById(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

		var interests = userInterestRepository.findByUser_Id(userId).stream()
				.map(UserInterest::getInterest)
				.map(i -> new InterestResponse(i.getId(), i.getName()))
				.toList();

		return toResponse(user, interests);
	}

	@Transactional
	public UserProfileResponse updateProfile(UUID userId, UpdateUserProfileRequest req) {
		var user = userRepository.findById(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

		patch(user, req);
		userRepository.save(user);

		List<InterestResponse> interests;
		if (req.interestIds() != null) {
			interests = replaceUserInterests(user, req.interestIds());
		} else {
			interests = userInterestRepository.findByUser_Id(userId).stream()
					.map(UserInterest::getInterest)
					.map(i -> new InterestResponse(i.getId(), i.getName()))
					.toList();
		}

		return toResponse(user, interests);
	}

	@Transactional(readOnly = true)
	public List<InterestResponse> listInterests() {
		return interestRepository.findAll().stream()
				.map(i -> new InterestResponse(i.getId(), i.getName()))
				.toList();
	}

	private static void patch(User user, UpdateUserProfileRequest req) {
		if (req.username() != null) user.setUsername(req.username());
		if (req.name() != null) user.setName(req.name());
		if (req.dateOfBirth() != null) user.setDateOfBirth(req.dateOfBirth());
		if (req.gender() != null) user.setGender(req.gender());
		if (req.sexualPreference() != null) user.setSexualPreference(req.sexualPreference());
		if (req.racialPreference() != null) user.setRacialPreference(req.racialPreference());
		if (req.meetingInterest() != null) user.setMeetingInterest(req.meetingInterest());
		if (req.locationCountry() != null) user.setLocationCountry(req.locationCountry());
		if (req.locationCity() != null) user.setLocationCity(req.locationCity());
		if (req.bio() != null) user.setBio(req.bio());
	}

	private List<InterestResponse> replaceUserInterests(User user, List<UUID> interestIds) {
		var uniqueIds = interestIds.stream().filter(id -> id != null).collect(Collectors.toSet());
		var interests = interestRepository.findAllById(uniqueIds);

		if (interests.size() != uniqueIds.size()) {
			Set<UUID> found = interests.stream().map(Interest::getId).collect(Collectors.toSet());
			var missing = uniqueIds.stream().filter(id -> !found.contains(id)).toList();
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown interestIds: " + missing);
		}

		userInterestRepository.deleteAllByUserId(user.getId());

		var rows = interests.stream().map(i -> {
			var ui = new UserInterest();
			ui.setUser(user);
			ui.setInterest(i);
			return ui;
		}).toList();

		userInterestRepository.saveAll(rows);

		return interests.stream()
				.map(i -> new InterestResponse(i.getId(), i.getName()))
				.toList();
	}

	private static UserProfileResponse toResponse(User user, List<InterestResponse> interests) {
		return new UserProfileResponse(
				user.getId(),
				user.getEmail(),
				user.getUsername(),
				user.getName(),
				user.getDateOfBirth(),
				user.getGender(),
				user.getSexualPreference(),
				user.getRacialPreference(),
				user.getMeetingInterest(),
				user.getLocationCountry(),
				user.getLocationCity(),
				user.getBio(),
				user.getRole(),
				user.getCreatedAt(),
				user.getUpdatedAt(),
				interests
		);
	}
}


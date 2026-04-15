package com.fsd10.merry_match_backend.service;

import com.fsd10.merry_match_backend.dto.MerryActionResponse;
import com.fsd10.merry_match_backend.dto.MerryListItemResponse;
import com.fsd10.merry_match_backend.dto.MerryListResponse;
import com.fsd10.merry_match_backend.entity.Match;
import com.fsd10.merry_match_backend.entity.User;
import com.fsd10.merry_match_backend.repository.MatchRepository;
import com.fsd10.merry_match_backend.repository.ProfileImageRepository;
import com.fsd10.merry_match_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MerryListService {
  private record MatchMeta(String status, Instant matchedAt) {}

  private final UserRepository userRepository;
  private final MatchRepository matchRepository;
  private final ProfileImageRepository profileImageRepository;

  @Transactional(readOnly = true)
  public MerryListResponse getMerryList(UUID currentUserId, int page, int limit) {
    int normalizedPage = Math.max(page, 1);
    int normalizedLimit = Math.min(Math.max(limit, 1), 100);
    int offset = (normalizedPage - 1) * normalizedLimit;

    List<User> users = userRepository.findMerryListPage(currentUserId, normalizedLimit, offset);
    List<UUID> userIds = users.stream().map(User::getId).toList();

    Map<UUID, MatchMeta> matchMetaByUserId = userIds.isEmpty()
        ? Map.of()
        : matchRepository.findPeerMatchStatusesForCurrentUser(currentUserId, userIds).stream()
            .collect(Collectors.toMap(
                MatchRepository.PeerMatchStatusView::getPeerId,
                row -> new MatchMeta(normalize(row.getStatus()), row.getMatchedAt()),
                (left, right) -> right
            ));

    Map<UUID, String> firstImageByUserId = userIds.isEmpty()
        ? Map.of()
        : profileImageRepository.findFirstImageByUserIds(userIds).stream()
            .collect(Collectors.toMap(ProfileImageRepository.UserImageView::getUserId, ProfileImageRepository.UserImageView::getImageUrl));

    List<MerryListItemResponse> items = users.stream()
        .map(user -> toMerryListItem(user, firstImageByUserId.get(user.getId()), matchMetaByUserId.get(user.getId())))
        .toList();

    LocalDate today = LocalDate.now(ZoneId.systemDefault());
    int merryToYou = safeCountOutgoingLikes(currentUserId);
    int merryMatch = safeCountActiveMatches(currentUserId);
    int limitUsed = safeCountLimitUsed(currentUserId, today);
    int limitMax = safeResolveLimitMax(currentUserId);

    return new MerryListResponse(items, merryToYou, merryMatch, limitUsed, limitMax);
  }

  @Transactional
  public MerryActionResponse merry(UUID currentUserId, UUID targetProfileId) {
    if (currentUserId.equals(targetProfileId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot merry yourself");
    }
    userRepository.findById(targetProfileId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target profile not found"));

    Match match = matchRepository.findActivePair(currentUserId, targetProfileId)
        .orElseGet(() -> matchRepository.save(
            Match.builder()
                .id(UUID.randomUUID())
                .user1Id(currentUserId)
                .user2Id(targetProfileId)
                .status("active")
                .build()
        ));

    return new MerryActionResponse(match.getId(), currentUserId, targetProfileId, match.getStatus());
  }

  @Transactional
  public void removeMerryFromList(UUID currentUserId, UUID targetProfileId) {
    if (currentUserId.equals(targetProfileId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot remove yourself");
    }
    userRepository.findById(targetProfileId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target profile not found"));
    userRepository.deleteOutgoingLikeToTarget(currentUserId, targetProfileId);
  }

  private MerryListItemResponse toMerryListItem(User user, String imageUrl, MatchMeta matchMeta) {
    String normalizedMatchStatus = matchMeta == null || matchMeta.status() == null ? "none" : matchMeta.status();
    boolean matched = isMatchedStatus(normalizedMatchStatus);
    boolean merryToday = matched && isTodayInstant(matchMeta == null ? null : matchMeta.matchedAt());
    return new MerryListItemResponse(
        user.getId(),
        user.getName(),
        calculateAge(user.getDateOfBirth()),
        buildLocation(user.getLocationCity(), user.getLocationCountry()),
        imageUrl,
        normalizedMatchStatus,
        matched,
        merryToday,
        user.getGender(),
        user.getSexualPreference(),
        user.getRacialPreference(),
        user.getMeetingInterest()
    );
  }

  private Integer calculateAge(LocalDate birthDate) {
    if (birthDate == null) {
      return null;
    }
    return Period.between(birthDate, LocalDate.now()).getYears();
  }

  private String buildLocation(String city, String country) {
    String trimmedCity = normalize(city);
    String trimmedCountry = normalize(country);
    if (trimmedCity == null && trimmedCountry == null) {
      return null;
    }
    if (trimmedCity == null) {
      return trimmedCountry;
    }
    if (trimmedCountry == null) {
      return trimmedCity;
    }
    return trimmedCity + ", " + trimmedCountry;
  }

  private String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private int safeResolveLimitMax(UUID currentUserId) {
    try {
      Integer planLimit = userRepository.findLimitMaxByUserId(currentUserId);
      return planLimit != null ? planLimit : 20;
    } catch (RuntimeException ignored) {
      return 20;
    }
  }

  private int safeCountLimitUsed(UUID currentUserId, LocalDate today) {
    try {
      return userRepository.countTodayQuotaUsage(
          currentUserId,
          today.atStartOfDay(ZoneId.systemDefault()).toInstant(),
          today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
      );
    } catch (RuntimeException ignored) {
      return 0;
    }
  }

  private int safeCountOutgoingLikes(UUID currentUserId) {
    try {
      return userRepository.countOutgoingLikes(currentUserId);
    } catch (RuntimeException ignored) {
      return 0;
    }
  }

  private int safeCountActiveMatches(UUID currentUserId) {
    try {
      return matchRepository.countActiveMatchByUserId(currentUserId);
    } catch (RuntimeException ignored) {
      return 0;
    }
  }

  private boolean isMatchedStatus(String status) {
    if (status == null) {
      return false;
    }
    return "active".equalsIgnoreCase(status) || "matched".equalsIgnoreCase(status);
  }

  private boolean isTodayInstant(Instant value) {
    if (value == null) {
      return false;
    }
    LocalDate date = value.atZone(ZoneId.systemDefault()).toLocalDate();
    return LocalDate.now(ZoneId.systemDefault()).isEqual(date);
  }
}

package com.fsd10.merry_match_backend.service;

import com.fsd10.merry_match_backend.dto.UserInterestsResponse;
import com.fsd10.merry_match_backend.entity.Interest;
import com.fsd10.merry_match_backend.entity.User;
import com.fsd10.merry_match_backend.entity.UserInterest;
import com.fsd10.merry_match_backend.repository.InterestRepository;
import com.fsd10.merry_match_backend.repository.UserInterestRepository;
import com.fsd10.merry_match_backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserInterestService {

  private final UserInterestRepository userInterestRepository;
  private final UserRepository userRepository;
  private final InterestRepository interestRepository;

  @Transactional
  public UserInterestsResponse replaceUserInterests(UUID userId, List<UUID> interestIds) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new EntityNotFoundException("User not found"));

    Set<UUID> deduped = new LinkedHashSet<>();
    if (interestIds != null) {
      for (UUID interestId : interestIds) {
        if (interestId != null) deduped.add(interestId);
      }
    }

    List<Interest> interests = interestRepository.findAllById(deduped);
    if (interests.size() != deduped.size()) {
      Set<UUID> found = interests.stream().map(Interest::getId).collect(Collectors.toSet());
      List<UUID> missing = deduped.stream().filter(id -> !found.contains(id)).toList();
      throw new IllegalArgumentException("Interest not found: " + missing);
    }

    userInterestRepository.deleteByUser_Id(userId);

    List<UserInterest> rows = new ArrayList<>(interests.size());
    List<UUID> savedInterestIds = new ArrayList<>(interests.size());
    for (Interest interest : interests) {
      UserInterest ui = new UserInterest();
      ui.setUser(user);
      ui.setInterest(interest);
      rows.add(ui);
      savedInterestIds.add(interest.getId());
    }
    userInterestRepository.saveAll(rows);

    return UserInterestsResponse.builder()
        .userId(userId)
        .interestIds(savedInterestIds)
        .build();
  }
}

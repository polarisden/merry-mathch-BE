package com.fsd10.merry_match_backend.service;

import com.fsd10.merry_match_backend.dto.UserInterestsResponse;
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

    for (UUID interestId : deduped) {
      if (!interestRepository.existsById(interestId)) {
        throw new IllegalArgumentException("Interest not found: " + interestId);
      }
    }

    userInterestRepository.deleteByUserId(userId);

    List<UUID> savedInterestIds = new ArrayList<>();
    for (UUID interestId : deduped) {
      userInterestRepository.save(UserInterest.builder()
          .userId(user.getId())
          .interestId(interestId)
          .build());
      savedInterestIds.add(interestId);
    }

    return UserInterestsResponse.builder()
        .userId(userId)
        .interestIds(savedInterestIds)
        .build();
  }
}

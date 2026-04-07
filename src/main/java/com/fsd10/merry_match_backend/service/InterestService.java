package com.fsd10.merry_match_backend.service;

import com.fsd10.merry_match_backend.entity.Interest;
import com.fsd10.merry_match_backend.repository.InterestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InterestService {

  private final InterestRepository interestRepository;

  public List<Interest> getAllInterests() {
    return interestRepository.findAll();
  }

  public Interest createOrGet(String name) {
    if (name == null) throw new IllegalArgumentException("name is required");
    String trimmed = name.trim();
    if (trimmed.isEmpty()) throw new IllegalArgumentException("name is required");

    String normalized = normalizeInterestName(trimmed);
    return interestRepository.findByNormalizedName(normalized)
        .orElseGet(() -> interestRepository.save(Interest.builder()
            .id(UUID.randomUUID())
            .name(trimmed)
            .normalizedName(normalized)
            .createdAt(Instant.now())
            .build()));
  }

  private String normalizeInterestName(String name) {
    // normalize: trim, collapse whitespace, lowercase
    String collapsed = name.trim().replaceAll("\\s+", " ");
    return collapsed.toLowerCase(Locale.ROOT);
  }
}

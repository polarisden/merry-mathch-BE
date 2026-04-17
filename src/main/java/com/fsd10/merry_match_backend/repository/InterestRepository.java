package com.fsd10.merry_match_backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fsd10.merry_match_backend.entity.Interest;

public interface InterestRepository extends JpaRepository<Interest, UUID> {
  Optional<Interest> findByNormalizedName(String normalizedName);
}

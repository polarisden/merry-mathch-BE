package com.fsd10.merry_match_backend.repository;

import com.fsd10.merry_match_backend.entity.UserInterest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface UserInterestRepository extends JpaRepository<UserInterest, UUID> {
  List<UserInterest> findByUserId(UUID userId);

  @Transactional
  void deleteByUserId(UUID userId);
}

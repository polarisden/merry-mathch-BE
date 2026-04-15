package com.fsd10.merry_match_backend.repository;

import com.fsd10.merry_match_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByEmail(String email);

  Optional<User> findByUsername(String username);

  @Modifying
  @Query("UPDATE User u SET u.merryCount = COALESCE(u.merryCount, 0) + 1 WHERE u.id = :userId")
  int incrementMerryCount(@Param("userId") UUID userId);
}


package com.fsd10.merry_match_backend.repository;

import com.fsd10.merry_match_backend.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {

  @Query("SELECT m FROM Match m WHERE m.user1Id = :userId OR m.user2Id = :userId")
  List<Match> findAllByUserId(@Param("userId") UUID userId);

  @Query("SELECT m FROM Match m WHERE (m.user1Id = :id1 AND m.user2Id = :id2) OR (m.user1Id = :id2 AND m.user2Id = :id1)")
  Optional<Match> findByBothUsers(@Param("id1") UUID id1, @Param("id2") UUID id2);
}

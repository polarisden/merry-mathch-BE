package com.fsd10.merry_match_backend.repository;

import com.fsd10.merry_match_backend.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {
}

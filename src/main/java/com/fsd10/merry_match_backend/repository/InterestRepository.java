package com.fsd10.merry_match_backend.repository;

import com.fsd10.merry_match_backend.entity.Interest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InterestRepository extends JpaRepository<Interest, UUID> {
}

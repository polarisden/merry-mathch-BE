package com.fsd10.merry_match_backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fsd10.merry_match_backend.entity.UserInterest;

public interface UserInterestRepository extends JpaRepository<UserInterest, UUID> {
	List<UserInterest> findByUser_Id(UUID userId);

	void deleteByUser_Id(UUID userId);
}

package com.fsd10.merry_match_backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fsd10.merry_match_backend.entity.UserInterest;

public interface UserInterestRepository extends JpaRepository<UserInterest, UUID> {
	List<UserInterest> findByUser_Id(UUID userId);

	/**
	 * Use JPQL bulk delete to avoid stale persistence context issues and ensure
	 * (user_id, interest_id) uniqueness is not violated when re-inserting.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from UserInterest ui where ui.user.id = :userId")
	int deleteAllByUserId(@Param("userId") UUID userId);
}

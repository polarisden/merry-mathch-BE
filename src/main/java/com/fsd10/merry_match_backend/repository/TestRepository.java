package com.fsd10.merry_match_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.fsd10.merry_match_backend.entity.Test;

public interface TestRepository extends JpaRepository<Test, Long> {
}
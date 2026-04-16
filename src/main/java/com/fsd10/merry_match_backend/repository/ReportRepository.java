package com.fsd10.merry_match_backend.repository;

import com.fsd10.merry_match_backend.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

  @Query("SELECT r FROM Report r ORDER BY r.createdAt DESC")
  List<Report> findAllOrderByCreatedAtDesc();
}

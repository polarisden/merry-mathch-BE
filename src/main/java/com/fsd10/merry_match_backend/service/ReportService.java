package com.fsd10.merry_match_backend.service;

import com.fsd10.merry_match_backend.dto.CreateReportRequest;
import com.fsd10.merry_match_backend.dto.ReportDetailResponse;
import com.fsd10.merry_match_backend.dto.ReportListItem;
import com.fsd10.merry_match_backend.dto.PatchReportStatusRequest;
import com.fsd10.merry_match_backend.entity.Report;
import com.fsd10.merry_match_backend.entity.ReportStatus;
import com.fsd10.merry_match_backend.entity.User;
import com.fsd10.merry_match_backend.repository.ReportRepository;
import com.fsd10.merry_match_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

  private final ReportRepository reportRepository;
  private final UserRepository userRepository;

  /** User submits a new complaint. Status starts as NEW. */
  @Transactional
  public ReportDetailResponse createReport(UUID reporterId, CreateReportRequest req) {
    if (req.getIssue() == null || req.getIssue().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "issue is required");
    }
    if (req.getDescription() == null || req.getDescription().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "description is required");
    }

    User reporter = userRepository.findById(reporterId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

    Report report = Report.builder()
        .reporterId(reporterId)
        .issue(req.getIssue().trim())
        .description(req.getDescription().trim())
        .status(ReportStatus.NEW)
        .build();

    report = reportRepository.save(report);
    return toDetail(report, reporter.getName());
  }

  /** Admin: list all complaints, newest first. */
  @Transactional(readOnly = true)
  public List<ReportListItem> listComplaints() {
    return reportRepository.findAllOrderByCreatedAtDesc().stream()
        .map(r -> {
          String reporterName = userRepository.findById(r.getReporterId())
              .map(User::getName)
              .orElse("Unknown");
          return toListItem(r, reporterName);
        })
        .toList();
  }

  /**
   * Admin: get complaint detail.
   * Automatically transitions status from NEW → PENDING when the admin first views it.
   */
  @Transactional
  public ReportDetailResponse getComplaintDetail(UUID reportId) {
    Report report = findReport(reportId);

    if (report.getStatus() == ReportStatus.NEW) {
      report.setStatus(ReportStatus.PENDING);
      report = reportRepository.save(report);
    }

    String reporterName = userRepository.findById(report.getReporterId())
        .map(User::getName)
        .orElse("Unknown");

    return toDetail(report, reporterName);
  }

  /** Admin: update status (resolved / cancel). */
  @Transactional
  public ReportDetailResponse updateStatus(UUID reportId, PatchReportStatusRequest req) {
    if (req.getStatus() == null || req.getStatus().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
    }

    ReportStatus newStatus = ReportStatus.fromDbValue(req.getStatus());
    Report report = findReport(reportId);
    report.setStatus(newStatus);
    report = reportRepository.save(report);

    String reporterName = userRepository.findById(report.getReporterId())
        .map(User::getName)
        .orElse("Unknown");

    return toDetail(report, reporterName);
  }

  private Report findReport(UUID id) {
    return reportRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
  }

  private ReportListItem toListItem(Report r, String reporterName) {
    return ReportListItem.builder()
        .id(r.getId())
        .reporterName(reporterName)
        .issue(r.getIssue())
        .description(r.getDescription())
        .status(r.getStatus().getDbValue())
        .createdAt(r.getCreatedAt())
        .build();
  }

  private ReportDetailResponse toDetail(Report r, String reporterName) {
    return ReportDetailResponse.builder()
        .id(r.getId())
        .reporterName(reporterName)
        .issue(r.getIssue())
        .description(r.getDescription())
        .status(r.getStatus().getDbValue())
        .createdAt(r.getCreatedAt())
        .build();
  }
}

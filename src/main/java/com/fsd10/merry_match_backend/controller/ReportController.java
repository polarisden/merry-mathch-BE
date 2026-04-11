package com.fsd10.merry_match_backend.controller;

import com.fsd10.merry_match_backend.auth.SupabaseJwtService;
import com.fsd10.merry_match_backend.dto.CreateReportRequest;
import com.fsd10.merry_match_backend.dto.PatchReportStatusRequest;
import com.fsd10.merry_match_backend.dto.ReportDetailResponse;
import com.fsd10.merry_match_backend.dto.ReportListItem;
import com.fsd10.merry_match_backend.entity.User;
import com.fsd10.merry_match_backend.repository.UserRepository;
import com.fsd10.merry_match_backend.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ReportController {

  private final ReportService reportService;
  private final SupabaseJwtService supabaseJwtService;
  private final UserRepository userRepository;

  /** POST /api/reports — user submits a complaint */
  @PostMapping("/api/reports")
  public ResponseEntity<ReportDetailResponse> createReport(
      @RequestHeader(name = "Authorization", required = false) String authorization,
      @RequestBody CreateReportRequest body) {
    UUID userId = requireUser(authorization);
    ReportDetailResponse result = reportService.createReport(userId, body);
    return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }

  /** GET /api/admin/complaints — admin lists all complaints */
  @GetMapping("/api/admin/complaints")
  public ResponseEntity<List<ReportListItem>> listComplaints(
      @RequestHeader(name = "Authorization", required = false) String authorization) {
    requireAdmin(authorization);
    return ResponseEntity.ok(reportService.listComplaints());
  }

  /** GET /api/admin/complaints/{id} — admin views detail; status auto-transitions new→pending */
  @GetMapping("/api/admin/complaints/{id}")
  public ResponseEntity<ReportDetailResponse> getComplaintDetail(
      @RequestHeader(name = "Authorization", required = false) String authorization,
      @PathVariable UUID id) {
    requireAdmin(authorization);
    return ResponseEntity.ok(reportService.getComplaintDetail(id));
  }

  /** PATCH /api/admin/complaints/{id}/status — admin updates status (resolved / cancel) */
  @PatchMapping("/api/admin/complaints/{id}/status")
  public ResponseEntity<ReportDetailResponse> updateComplaintStatus(
      @RequestHeader(name = "Authorization", required = false) String authorization,
      @PathVariable UUID id,
      @RequestBody PatchReportStatusRequest body) {
    requireAdmin(authorization);
    return ResponseEntity.ok(reportService.updateStatus(id, body));
  }

  private void requireAdmin(String authorization) {
    UUID userId = requireUser(authorization);
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    if (user.getRole() == null || !user.getRole().equalsIgnoreCase("admin")) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
  }

  private UUID requireUser(String authorization) {
    if (authorization == null || authorization.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    try {
      return supabaseJwtService.requireUserIdFromAuthorization(authorization);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    }
  }
}

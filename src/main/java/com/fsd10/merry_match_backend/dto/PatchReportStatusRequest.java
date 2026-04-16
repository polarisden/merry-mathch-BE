package com.fsd10.merry_match_backend.dto;

import lombok.Data;

@Data
public class PatchReportStatusRequest {
  /** One of: "pending", "resolved", "cancel" */
  private String status;
}

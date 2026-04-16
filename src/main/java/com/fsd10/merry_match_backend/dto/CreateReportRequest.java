package com.fsd10.merry_match_backend.dto;

import lombok.Data;

@Data
public class CreateReportRequest {
  private String issue;
  private String description;
}

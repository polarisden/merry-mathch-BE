package com.fsd10.merry_match_backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
  // Keep supabase field names for easy client consumption
  private String access_token;
  private String refresh_token;
  private String token_type;
  private Integer expires_in;
}


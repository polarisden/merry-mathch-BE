package com.fsd10.merry_match_backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class RegisterResponse {
  private UUID id;
  private String email;

  private String username;
  private String name;
  private LocalDate dateOfBirth;

  private String gender;
  private String sexualPreference;
  private String racialPreference;
  private String meetingInterest;
  private String locationCountry;
  private String locationCity;

  private String bio;

  private Instant createdAt;
  private Instant updatedAt;

  private String role;

  // Return auth tokens so FE can auto-login after register.
  private String access_token;
  private String refresh_token;
  private String token_type;
  private Integer expires_in;
}


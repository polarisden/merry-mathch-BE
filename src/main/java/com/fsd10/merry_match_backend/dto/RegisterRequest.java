package com.fsd10.merry_match_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class RegisterRequest {

  @NotBlank
  @Email
  private String email;

  @NotBlank
  @Size(min = 6, max = 72)
  private String password;

  @NotBlank
  private String username;

  @NotBlank
  private String name;

  private LocalDate dateOfBirth;

  // FE: sexualIdentity -> DB: gender
  private String sexualIdentity;
  private String sexualPreference;
  private String racialPreference;
  private String meetingInterest;

  // FE: location -> DB: location_country
  private String location;

  // FE: city -> DB: location_city
  private String city;

  private String bio;

  // Base64 data URLs from FE (used only during register for initial profile photos)
  private List<String> photos;
}


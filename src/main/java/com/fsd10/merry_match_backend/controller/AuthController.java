package com.fsd10.merry_match_backend.controller;

import com.fsd10.merry_match_backend.dto.RegisterRequest;
import com.fsd10.merry_match_backend.dto.RegisterResponse;
import com.fsd10.merry_match_backend.dto.LoginRequest;
import com.fsd10.merry_match_backend.dto.LoginResponse;
import com.fsd10.merry_match_backend.dto.AvailabilityResponse;
import com.fsd10.merry_match_backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @GetMapping("/check-availability")
  public ResponseEntity<AvailabilityResponse> checkAvailability(
      @RequestParam(required = false) String email,
      @RequestParam(required = false) String username
  ) {
    AvailabilityResponse response = authService.checkAvailability(email, username);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/register")
  public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
    RegisterResponse response = authService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    LoginResponse response = authService.login(request);
    return ResponseEntity.ok(response);
  }
}


package com.fsd10.merry_match_backend.service;

import com.fsd10.merry_match_backend.dto.RegisterRequest;
import com.fsd10.merry_match_backend.dto.RegisterResponse;
import com.fsd10.merry_match_backend.dto.LoginRequest;
import com.fsd10.merry_match_backend.dto.LoginResponse;
import com.fsd10.merry_match_backend.dto.AvailabilityResponse;
import com.fsd10.merry_match_backend.entity.User;
import com.fsd10.merry_match_backend.exception.EmailAlreadyUsedException;
import com.fsd10.merry_match_backend.exception.LoginFailedException;
import com.fsd10.merry_match_backend.exception.RegisterFailedException;
import com.fsd10.merry_match_backend.exception.UsernameAlreadyUsedException;
import com.fsd10.merry_match_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

  private final UserRepository userRepository;
  private final ProfileImageService profileImageService;

  /** Parsed Supabase /auth/v1/signup response (tokens optional). */
  private record SignupResult(
      UUID userId,
      String accessToken,
      String refreshToken,
      String tokenType,
      Integer expiresIn
  ) {}

  @Value("${supabase.url}")
  private String supabaseUrl;

  @Value("${supabase.apiKey}")
  private String supabaseApiKey;

  public AvailabilityResponse checkAvailability(String email, String username) {
    String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
    String normalizedUsername = username == null ? "" : username.trim();

    boolean emailAvailable = normalizedEmail.isBlank()
        || userRepository.findByEmail(normalizedEmail).isEmpty();
    boolean usernameAvailable = normalizedUsername.isBlank()
        || userRepository.findByUsername(normalizedUsername).isEmpty();

    return AvailabilityResponse.builder()
        .emailAvailable(emailAvailable)
        .usernameAvailable(usernameAvailable)
        .build();
  }

  public RegisterResponse register(RegisterRequest request) {
    String email = request.getEmail().trim().toLowerCase();
    String username = request.getUsername().trim();
    String rawPassword = request.getPassword();
    log.info("register: email={}, username={}, hasPhotos={}", email, username,
        request.getPhotos() != null && !request.getPhotos().isEmpty());

    if (userRepository.findByEmail(email).isPresent()) {
      throw new EmailAlreadyUsedException(email);
    }

    if (userRepository.findByUsername(username).isPresent()) {
      throw new UsernameAlreadyUsedException(username);
    }

    SignupResult signup = signupSupabaseAuth(email, rawPassword);
    UUID id = signup.userId();
    Instant now = Instant.now();

    User user = User.builder()
        .id(id)
        .email(email)
        .username(username)
        .name(request.getName())
        .dateOfBirth(request.getDateOfBirth())
        // FE: sexualIdentity -> DB: gender
        .gender(request.getSexualIdentity())
        .sexualPreference(request.getSexualPreference())
        .racialPreference(request.getRacialPreference())
        .meetingInterest(request.getMeetingInterest())
        // FE: location -> DB: location_country
        .locationCountry(request.getLocation())
        // FE: city -> DB: location_city
        .locationCity(request.getCity())
        .bio(request.getBio())
        .createdAt(now)
        .updatedAt(now)
        .role("user")
        .build();

    userRepository.save(user);

    // Optional initial profile photos sent as data URLs from FE.
    if (request.getPhotos() != null && !request.getPhotos().isEmpty()) {
      profileImageService.uploadDataUrlPhotosForNewUser(id, request.getPhotos());
    }

    LoginResponse loginResponse = resolveSessionAfterSignup(email, rawPassword, signup);

    return RegisterResponse.builder()
        .id(id)
        .email(email)
        .username(user.getUsername())
        .name(user.getName())
        .dateOfBirth(user.getDateOfBirth())
        .gender(user.getGender())
        .sexualPreference(user.getSexualPreference())
        .racialPreference(user.getRacialPreference())
        .meetingInterest(user.getMeetingInterest())
        .locationCountry(user.getLocationCountry())
        .locationCity(user.getLocationCity())
        .bio(user.getBio())
        .createdAt(user.getCreatedAt())
        .updatedAt(user.getUpdatedAt())
        .role(user.getRole())
        .access_token(loginResponse.getAccess_token())
        .refresh_token(loginResponse.getRefresh_token())
        .token_type(loginResponse.getToken_type())
        .expires_in(loginResponse.getExpires_in())
        .build();
  }

  /**
   * Prefer tokens returned by signup. Otherwise try password login.
   * If login fails (e.g. email confirmation required), return empty tokens but registration still succeeded.
   */
  private LoginResponse resolveSessionAfterSignup(String email, String rawPassword, SignupResult signup) {
    if (signup.accessToken() != null && !signup.accessToken().isBlank()) {
      return LoginResponse.builder()
          .access_token(signup.accessToken())
          .refresh_token(signup.refreshToken())
          .token_type(signup.tokenType())
          .expires_in(signup.expiresIn())
          .build();
    }
    try {
      return loginSupabaseAuth(email, rawPassword);
    } catch (LoginFailedException e) {
      log.warn("Post-signup login failed (user may need email confirmation): {}", e.getMessage());
      return LoginResponse.builder().build();
    }
  }

  /**
   * Login via Supabase Auth (password grant).
   * NOTE: Your current `users` table does NOT contain password, so this method relies on `auth.users`.
   */
  public LoginResponse login(LoginRequest request) {
    String email = request.getEmail().trim().toLowerCase();
    String rawPassword = request.getPassword();
    return loginSupabaseAuth(email, rawPassword);
  }

  private LoginResponse loginSupabaseAuth(String email, String rawPassword) {
    String restBaseUrl = resolveSupabaseRestBaseUrl(supabaseUrl);
    String tokenUrl = restBaseUrl + "/auth/v1/token?grant_type=password";

    String body = "{\"email\":\"" + escapeJson(email) + "\",\"password\":\"" + escapeJson(rawPassword) + "\"}";

    HttpClient client = HttpClient.newHttpClient();
    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(tokenUrl))
        .header("apikey", supabaseApiKey)
        .header("Authorization", "Bearer " + supabaseApiKey)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();

    try {
      HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        log.warn("Supabase login failed: status={}, body={}", response.statusCode(), truncateForLog(response.body()));
        String msg = extractStringClaim(response.body(), "error_description");
        if (msg == null) msg = extractStringClaim(response.body(), "msg");
        if (msg == null) msg = "Login failed";
        throw new LoginFailedException(msg + " (status=" + response.statusCode() + ")");
      }

      String accessToken = extractStringClaim(response.body(), "access_token");
      String refreshToken = extractStringClaim(response.body(), "refresh_token");
      String tokenType = extractStringClaim(response.body(), "token_type");
      Integer expiresIn = extractIntClaim(response.body(), "expires_in");

      if (accessToken == null) {
        throw new LoginFailedException("Login failed: access_token missing");
      }

      return LoginResponse.builder()
          .access_token(accessToken)
          .refresh_token(refreshToken)
          .token_type(tokenType)
          .expires_in(expiresIn)
          .build();
    } catch (IOException | InterruptedException e) {
      throw new LoginFailedException("Login request error: " + e.getMessage());
    }
  }

  private SignupResult signupSupabaseAuth(String email, String rawPassword) {
    String restBaseUrl = resolveSupabaseRestBaseUrl(supabaseUrl);
    String signupUrl = restBaseUrl + "/auth/v1/signup";

    String body = "{\"email\":\"" + escapeJson(email) + "\",\"password\":\"" + escapeJson(rawPassword) + "\"}";

    HttpClient client = HttpClient.newHttpClient();
    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(signupUrl))
        .header("apikey", supabaseApiKey)
        .header("Authorization", "Bearer " + supabaseApiKey)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();

    try {
      HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        log.warn("Supabase signup failed: status={}, body={}", response.statusCode(), truncateForLog(response.body()));
        String msg = extractStringClaim(response.body(), "error_description");
        if (msg == null) msg = extractStringClaim(response.body(), "msg");
        if (msg == null) msg = "Register failed";
        if (msg.toLowerCase().contains("already")) {
          throw new EmailAlreadyUsedException(email);
        }
        throw new RegisterFailedException(msg + " (status=" + response.statusCode() + ")");
      }

      String json = response.body();
      log.debug("Supabase signup response (truncated): {}", truncateForLog(json));
      UUID userId = extractAuthUserIdFromSignupJson(json);
      if (userId == null) {
        log.error("Could not parse user id from signup JSON: {}", truncateForLog(json));
        throw new RegisterFailedException("Register failed: user id missing from Supabase response");
      }
      String accessToken = extractStringClaim(json, "access_token");
      String refreshToken = extractStringClaim(json, "refresh_token");
      String tokenType = extractStringClaim(json, "token_type");
      Integer expiresIn = extractIntClaim(json, "expires_in");
      return new SignupResult(userId, accessToken, refreshToken, tokenType, expiresIn);
    } catch (IOException | InterruptedException e) {
      log.error("Supabase signup HTTP error", e);
      throw new RegisterFailedException("Register request error: " + e.getMessage());
    }
  }

  private static String truncateForLog(String s) {
    if (s == null) {
      return "";
    }
    int max = 2000;
    if (s.length() <= max) {
      return s;
    }
    return s.substring(0, max) + "... (truncated)";
  }

  /**
   * Prefer {@code user.id} from signup JSON (first UUID after the {@code "user"} key).
   */
  private UUID extractAuthUserIdFromSignupJson(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    int userKey = json.indexOf("\"user\"");
    if (userKey >= 0) {
      String fromUser = json.substring(userKey);
      Matcher m = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F-]{36})\"").matcher(fromUser);
      if (m.find()) {
        try {
          return UUID.fromString(m.group(1));
        } catch (IllegalArgumentException ignore) {
          // fall through
        }
      }
    }
    String topLevelId = extractStringClaim(json, "id");
    if (topLevelId != null) {
      try {
        return UUID.fromString(topLevelId);
      } catch (IllegalArgumentException ignore) {
        return null;
      }
    }
    return null;
  }

  private String escapeJson(String s) {
    // Minimal escaping for email/password strings
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private String resolveSupabaseRestBaseUrl(String url) {
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("supabase.url is not configured");
    }
    String u = url.trim();

    if (u.contains("dashboard/project/")) {
      String projectRef = u.substring(u.indexOf("dashboard/project/") + "dashboard/project/".length());
      if (projectRef.contains("/")) {
        projectRef = projectRef.substring(0, projectRef.indexOf('/'));
      }
      return "https://" + projectRef + ".supabase.co";
    }

    if (u.contains(".supabase.co")) {
      return u.replaceAll("/+$", "");
    }

    if (!u.contains("http")) {
      return "https://" + u + ".supabase.co";
    }

    return u.replaceAll("/+$", "");
  }

  /**
   * Tiny JSON claim extractor using regex to avoid explicit Jackson dependencies.
   */
  private String extractStringClaim(String json, String claimName) {
    Pattern pattern = Pattern.compile("\"" + Pattern.quote(claimName) + "\"\\s*:\\s*\"([^\"]+)\"");
    Matcher matcher = pattern.matcher(json);
    if (!matcher.find()) return null;
    return matcher.group(1);
  }

  private Integer extractIntClaim(String json, String claimName) {
    Pattern pattern = Pattern.compile("\"" + Pattern.quote(claimName) + "\"\\s*:\\s*(\\d+)");
    Matcher matcher = pattern.matcher(json);
    if (!matcher.find()) return null;
    try {
      return Integer.parseInt(matcher.group(1));
    } catch (NumberFormatException ignore) {
      return null;
    }
  }
}


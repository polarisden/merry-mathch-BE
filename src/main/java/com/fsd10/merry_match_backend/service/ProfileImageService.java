package com.fsd10.merry_match_backend.service;

import com.fsd10.merry_match_backend.dto.ProfileImageUploadResponse;
import com.fsd10.merry_match_backend.entity.ProfileImage;
import com.fsd10.merry_match_backend.entity.User;
import com.fsd10.merry_match_backend.repository.ProfileImageRepository;
import com.fsd10.merry_match_backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileImageService {

  private final ProfileImageRepository profileImageRepository;
  private final UserRepository userRepository;

  @Value("${supabase.url}")
  private String supabaseUrl;

  @Value("${supabase.bucket}")
  private String supabaseBucket;

  @Value("${supabase.apiKey}")
  private String supabaseApiKey;

  /**
   * Used by standalone profile image upload endpoint (multipart/form-data).
   */
  public ProfileImageUploadResponse uploadForUser(UUID userId, MultipartFile file, boolean isPrimary) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("File is required");
    }

    String contentType = file.getContentType();
    if (contentType != null && !contentType.startsWith("image/")) {
      throw new IllegalArgumentException("Only image upload is allowed");
    }

    // Ensure user exists
    userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));

    UUID imageId = UUID.randomUUID();
    String originalFilename = Objects.requireNonNullElse(file.getOriginalFilename(), "image");

    // Make filename URL-safe for storage REST API
    String safeFilename = originalFilename
        .replaceAll("[\\\\/]", "_")
        .replaceAll("\\s+", "_");

    String objectName = "users/" + userId + "/" + imageId + "_" + safeFilename;

    String restBaseUrl = resolveSupabaseRestBaseUrl(supabaseUrl);
    String uploadUrl = restBaseUrl + "/storage/v1/object/" + supabaseBucket + "/" + objectName;

    uploadToSupabase(uploadUrl, contentType, file, supabaseApiKey);

    // public bucket => public URL directly
    String publicUrl = restBaseUrl + "/storage/v1/object/public/" + supabaseBucket + "/" + objectName;

    ProfileImage saved;
    if (isPrimary) {
      saved = savePrimary(userId, imageId, publicUrl);
    } else {
      saved = saveNonPrimary(userId, imageId, publicUrl);
    }

    return ProfileImageUploadResponse.builder()
        .id(saved.getId())
        .userId(saved.getUserId())
        .imageUrl(saved.getImageUrl())
        .isPrimary(saved.isPrimary())
        .createdAt(saved.getCreatedAt())
        .build();
  }

  @Transactional
  protected ProfileImage savePrimary(UUID userId, UUID imageId, String publicUrl) {
    profileImageRepository.clearPrimaryByUserId(userId);

    ProfileImage img = ProfileImage.builder()
        .id(imageId)
        .userId(userId)
        .imageUrl(publicUrl)
        .isPrimary(true)
        .createdAt(Instant.now())
        .build();

    return profileImageRepository.save(img);
  }

  /**
   * Used during register flow when FE sends photos as data URLs (base64).
   * Does NOT require JWT because we already know the userId.
   * First valid image will be marked as primary; others as non-primary.
   */
  @Transactional
  public void uploadDataUrlPhotosForNewUser(UUID userId, List<String> photos) {
    if (photos == null || photos.isEmpty()) {
      return;
    }

    // Ensure user exists (defensive)
    userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));

    boolean primarySet = false;
    String restBaseUrl = resolveSupabaseRestBaseUrl(supabaseUrl);

    for (String dataUrl : photos) {
      if (dataUrl == null || dataUrl.isBlank()) {
        continue;
      }

      ParsedDataUrl parsed;
      try {
        parsed = parseDataUrl(dataUrl);
      } catch (IllegalArgumentException e) {
        log.warn("Skipping invalid data URL image: {}", e.getMessage());
        continue;
      }

      UUID imageId = UUID.randomUUID();
      String extension = parsed.extension != null ? parsed.extension : "png";
      String objectName = "users/" + userId + "/" + imageId + "." + extension;

      String uploadUrl = restBaseUrl + "/storage/v1/object/" + supabaseBucket + "/" + objectName;
      uploadBytesToSupabase(uploadUrl, parsed.contentType, parsed.bytes, supabaseApiKey);

      String publicUrl = restBaseUrl + "/storage/v1/object/public/" + supabaseBucket + "/" + objectName;

      if (!primarySet) {
        savePrimary(userId, imageId, publicUrl);
        primarySet = true;
      } else {
        saveNonPrimary(userId, imageId, publicUrl);
      }
    }
  }

  protected ProfileImage saveNonPrimary(UUID userId, UUID imageId, String publicUrl) {
    ProfileImage img = ProfileImage.builder()
        .id(imageId)
        .userId(userId)
        .imageUrl(publicUrl)
        .isPrimary(false)
        .createdAt(Instant.now())
        .build();

    return profileImageRepository.save(img);
  }

  /**
   * Extract userId from JWT without Jackson:
   * - decode payload (base64url)
   * - read claim "sub" / "user_id" / "id" / "email" fallback
   */
  public UUID extractUserIdFromJwt(String bearerToken) {
    if (bearerToken == null || bearerToken.isBlank()) {
      throw new IllegalArgumentException("Missing Authorization header");
    }

    String token = bearerToken.trim();
    if (token.toLowerCase().startsWith("bearer ")) {
      token = token.substring(7).trim();
    }

    String[] parts = token.split("\\.");
    if (parts.length < 2) {
      throw new IllegalArgumentException("Invalid JWT format");
    }

    String payloadJson = new String(
        Base64.getUrlDecoder().decode(parts[1]),
        StandardCharsets.UTF_8
    );

    UUID fromSub = extractUuidClaim(payloadJson, "sub");
    if (fromSub != null) return fromSub;

    UUID fromUserId = extractUuidClaim(payloadJson, "user_id");
    if (fromUserId != null) return fromUserId;

    UUID fromId = extractUuidClaim(payloadJson, "id");
    if (fromId != null) return fromId;

    String email = extractStringClaim(payloadJson, "email");
    if (email != null && !email.isBlank()) {
      User user = userRepository.findByEmail(email.toLowerCase().trim())
          .orElseThrow(() -> new EntityNotFoundException("User not found for email: " + email));
      return user.getId();
    }

    throw new IllegalArgumentException("Cannot extract user id from JWT claims");
  }

  private UUID extractUuidClaim(String payloadJson, String claimName) {
    String v = extractStringClaim(payloadJson, claimName);
    if (v == null || v.isBlank()) return null;
    try {
      return UUID.fromString(v);
    } catch (IllegalArgumentException ignore) {
      return null;
    }
  }

  private String extractStringClaim(String payloadJson, String claimName) {
    // Matches: "claimName":"<value>"
    Pattern pattern = Pattern.compile("\"" + Pattern.quote(claimName) + "\"\\s*:\\s*\"([^\"]+)\"");
    Matcher matcher = pattern.matcher(payloadJson);
    if (!matcher.find()) return null;
    return matcher.group(1);
  }

  private String resolveSupabaseRestBaseUrl(String url) {
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("supabase.url is not configured");
    }
    String u = url.trim();

    // If dashboard link like:
    // https://supabase.com/dashboard/project/<project-ref>
    if (u.contains("dashboard/project/")) {
      String projectRef = u.substring(u.indexOf("dashboard/project/") + "dashboard/project/".length());
      if (projectRef.contains("/")) {
        projectRef = projectRef.substring(0, projectRef.indexOf('/'));
      }
      return "https://" + projectRef + ".supabase.co";
    }

    // Already looks like https://<ref>.supabase.co
    if (u.contains(".supabase.co")) {
      return u.replaceAll("/+$", "");
    }

    // If user passed only <project-ref>
    if (!u.contains("http")) {
      return "https://" + u + ".supabase.co";
    }

    return u.replaceAll("/+$", "");
  }

  private void uploadToSupabase(String uploadUrl, String contentType, MultipartFile file, String apiKey) {
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot read uploaded file", e);
    }
    uploadBytesToSupabase(uploadUrl, contentType, bytes, apiKey);
  }

  private void uploadBytesToSupabase(String uploadUrl, String contentType, byte[] bytes, String apiKey) {
    String ct = contentType != null ? contentType : "application/octet-stream";

    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(uploadUrl))
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", ct)
        .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
        .build();

    try {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        log.error("Supabase upload failed: status={}, body={}", response.statusCode(), response.body());
        throw new IllegalStateException("Supabase upload failed with status: " + response.statusCode());
      }
    } catch (IOException | InterruptedException e) {
      throw new IllegalStateException("Supabase upload error", e);
    }
  }

  private ParsedDataUrl parseDataUrl(String dataUrl) {
    // Example: data:image/png;base64,AAAA...
    int commaIndex = dataUrl.indexOf(',');
    if (commaIndex <= 0) {
      throw new IllegalArgumentException("Invalid data URL format");
    }
    String meta = dataUrl.substring(5, commaIndex); // skip "data:"
    String base64Part = dataUrl.substring(commaIndex + 1);

    if (!meta.contains(";base64")) {
      throw new IllegalArgumentException("Only base64 data URLs are supported");
    }
    String mimeType = meta.substring(0, meta.indexOf(';'));

    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(base64Part);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid base64 in data URL", e);
    }

    String extension;
    if ("image/jpeg".equalsIgnoreCase(mimeType) || "image/jpg".equalsIgnoreCase(mimeType)) {
      extension = "jpg";
    } else if ("image/png".equalsIgnoreCase(mimeType)) {
      extension = "png";
    } else if ("image/webp".equalsIgnoreCase(mimeType)) {
      extension = "webp";
    } else {
      extension = "bin";
    }

    return new ParsedDataUrl(mimeType, extension, bytes);
  }

  private record ParsedDataUrl(String contentType, String extension, byte[] bytes) {}
}


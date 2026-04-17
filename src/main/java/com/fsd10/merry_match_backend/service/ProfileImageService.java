package com.fsd10.merry_match_backend.service;

import com.fsd10.merry_match_backend.dto.ProfileImageUploadResponse;
import com.fsd10.merry_match_backend.dto.UserPictureResponse;
import com.fsd10.merry_match_backend.entity.ProfileImage;
import com.fsd10.merry_match_backend.repository.ProfileImageRepository;
import com.fsd10.merry_match_backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileImageService {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final ProfileImageRepository profileImageRepository;
  private final UserRepository userRepository;

  @Value("${supabase.url}")
  private String supabaseUrl;

  @Value("${supabase.bucket}")
  private String supabaseBucket;

  @Value("${supabase.apiKey}")
  private String supabaseApiKey;

  /** Max decoded image bytes per profile photo (register + upload). Default 5 MiB — under common API/storage limits. */
  @Value("${merry.profile-photo.max-bytes:5242880}")
  private int profilePhotoMaxBytes;

  /**
   * Uploads to the default profile bucket at {@code objectName}. Returns the public URL.
   */
  public String uploadPublicObject(String objectName, MultipartFile file) {
    return uploadPublicObjectToBucket(supabaseBucket, objectName, file);
  }

  /**
   * Upload to an arbitrary public bucket (e.g. {@code chat-images} for chat).
   */
  public String uploadPublicObjectToBucket(String bucketName, String objectName, MultipartFile file) {
    if (bucketName == null || bucketName.isBlank()) {
      throw new IllegalArgumentException("Bucket name is required");
    }
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("File is required");
    }
    String contentType = file.getContentType();
    if (contentType != null && !contentType.startsWith("image/")) {
      throw new IllegalArgumentException("Only image upload is allowed");
    }
    String restBaseUrl = resolveSupabaseRestBaseUrl(supabaseUrl);
    String uploadUrl = restBaseUrl + "/storage/v1/object/" + bucketName + "/" + objectName;
    uploadToSupabase(uploadUrl, contentType, file, supabaseApiKey);
    return restBaseUrl + "/storage/v1/object/public/" + bucketName + "/" + objectName;
  }

  /**
   * Creates a time-limited URL to read an object (works when the bucket is not public).
   *
   * @param objectPath path inside the bucket, e.g. {@code rooms/<roomId>/<file>.png}
   */
  public String createSignedUrlForObject(String bucket, String objectPath, long expiresInSeconds) {
    if (bucket == null || bucket.isBlank() || objectPath == null || objectPath.isBlank()) {
      throw new IllegalArgumentException("Bucket and object path are required");
    }
    String restBase = resolveSupabaseRestBaseUrl(supabaseUrl).replaceAll("/+$", "");
    String normalizedPath = objectPath.replaceAll("^/+", "");
    String signUrl = restBase + "/storage/v1/object/sign/" + bucket + "/" + normalizedPath;

    String jsonBody = "{\"expiresIn\":" + expiresInSeconds + "}";
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(signUrl))
            .header("Authorization", "Bearer " + supabaseApiKey)
            .header("apikey", supabaseApiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

    try {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        log.error(
            "Supabase sign URL failed: status={}, url={}, body={}",
            response.statusCode(),
            signUrl,
            truncateForLog(response.body()));
        throw new IllegalStateException("Supabase sign URL failed with status: " + response.statusCode());
      }
      JsonNode root = JSON.readTree(response.body());
      String rel = root.path("signedURL").asString(null);
      if (rel == null || rel.isBlank()) {
        rel = root.path("signedUrl").asString(null);
      }
      if (rel == null || rel.isBlank()) {
        throw new IllegalStateException("Supabase sign response missing signedURL");
      }
      if (rel.startsWith("http://") || rel.startsWith("https://")) {
        return rel;
      }
      if (rel.startsWith("/")) {
        // Supabase may return a path starting with "/object/sign/..." (missing "/storage/v1").
        // Normalize to a fetchable URL.
        if (rel.startsWith("/object/")) {
          return restBase + "/storage/v1" + rel;
        }
        return restBase + rel;
      }
      if (rel.startsWith("object/")) {
        return restBase + "/storage/v1/" + rel;
      }
      return restBase + "/" + rel;
    } catch (IOException | InterruptedException e) {
      throw new IllegalStateException("Supabase sign URL error", e);
    }
  }

  /**
   * If {@code url} is a {@code /object/public/{bucket}/...} URL, return a fresh signed URL; otherwise
   * return {@code url} unchanged (e.g. already signed).
   */
  public String refreshToSignedUrlIfPublicChatPath(String bucket, String url, long expiresInSeconds) {
    if (url == null || url.isBlank() || bucket == null || bucket.isBlank()) {
      return url;
    }
    if (url.contains("/object/sign/") || url.contains("token=")) {
      return url;
    }
    Optional<String> path = extractObjectPathFromPublicBucketUrl(bucket, url);
    if (path.isEmpty()) {
      return url;
    }
    try {
      return createSignedUrlForObject(bucket, path.get(), expiresInSeconds);
    } catch (Exception e) {
      log.warn("Could not refresh signed URL for chat image: {}", e.getMessage());
      return url;
    }
  }

  private Optional<String> extractObjectPathFromPublicBucketUrl(String bucket, String publicUrl) {
    String marker = "/object/public/" + bucket + "/";
    int i = publicUrl.indexOf(marker);
    if (i >= 0) {
      return Optional.of(publicUrl.substring(i + marker.length()));
    }
    return Optional.empty();
  }

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

  @Transactional(readOnly = true)
  public UserPictureResponse getUserPicture(UUID userId) {
    String url = profileImageRepository
        .findFirstByUserIdAndIsPrimaryTrueOrderByCreatedAtAsc(userId)
        .map(ProfileImage::getImageUrl)
        .orElse(null);
    return new UserPictureResponse(url);
  }

  @Transactional(readOnly = true)
  public List<ProfileImageUploadResponse> listForUser(UUID userId) {
    return profileImageRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(img -> ProfileImageUploadResponse.builder()
            .id(img.getId())
            .userId(img.getUserId())
            .imageUrl(img.getImageUrl())
            .isPrimary(img.isPrimary())
            .createdAt(img.getCreatedAt())
            .build())
        .toList();
  }

  @Transactional
  public void deleteForUser(UUID userId, UUID imageId) {
    ProfileImage img = profileImageRepository.findById(imageId)
        .filter(i -> userId.equals(i.getUserId()))
        .orElseThrow(() -> new EntityNotFoundException("Profile image not found"));

    // best-effort storage cleanup, then DB
    deleteObjectByPublicUrlIfPossible(img.getImageUrl());

    long deleted = profileImageRepository.deleteByIdAndUserId(imageId, userId);
    if (deleted == 0) throw new EntityNotFoundException("Profile image not found");
  }

  /**
   * Delete all profile images for a user, including Supabase Storage objects.
   * Best-effort: storage deletions are attempted and logged; DB cleanup always proceeds.
   */
  @Transactional
  public void deleteAllForUser(UUID userId) {
    List<ProfileImage> imgs = profileImageRepository.findByUserIdOrderByCreatedAtDesc(userId);
    for (ProfileImage img : imgs) {
      deleteObjectByPublicUrlIfPossible(img.getImageUrl());
    }
    profileImageRepository.deleteAllByUserId(userId);
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

      assertProfilePhotoSize(parsed.bytes);

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
    assertProfilePhotoSize(bytes);
    uploadBytesToSupabase(uploadUrl, contentType, bytes, apiKey);
  }

  private void assertProfilePhotoSize(byte[] bytes) {
    if (bytes == null || bytes.length <= profilePhotoMaxBytes) {
      return;
    }
    int maxMb = Math.max(1, profilePhotoMaxBytes / (1024 * 1024));
    throw new ResponseStatusException(
        HttpStatusCode.valueOf(413),
        "Profile image is too large (" + bytes.length + " bytes). Maximum is " + profilePhotoMaxBytes
            + " bytes (~" + maxMb + " MiB). Send a smaller or more compressed image.");
  }

  private void uploadBytesToSupabase(String uploadUrl, String contentType, byte[] bytes, String apiKey) {
    String ct = contentType != null ? contentType : "application/octet-stream";

    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(uploadUrl))
        .header("Authorization", "Bearer " + apiKey)
        .header("apikey", apiKey)
        .header("Content-Type", ct)
        .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
        .build();

    try {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        log.error("Supabase upload failed: status={}, body={}", response.statusCode(), response.body());
        if (isPayloadTooLargeResponse(response)) {
          throw new ResponseStatusException(
              HttpStatusCode.valueOf(413),
              "Storage rejected the image as too large. Try a smaller file or increase merry.profile-photo.max-bytes if your project allows it.");
        }
        throw new IllegalStateException("Supabase upload failed with status: " + response.statusCode());
      }
    } catch (IOException | InterruptedException e) {
      throw new IllegalStateException("Supabase upload error", e);
    }
  }

  private static boolean isPayloadTooLargeResponse(HttpResponse<String> response) {
    int code = response.statusCode();
    if (code == 413) {
      return true;
    }
    String body = response.body();
    if (code == 400 && body != null) {
      return body.contains("Payload too large") || body.contains("\"413\"");
    }
    return false;
  }

  private void deleteObjectByPublicUrlIfPossible(String publicUrl) {
    Optional<String> objectName = extractObjectNameFromPublicUrl(publicUrl);
    if (objectName.isEmpty()) return;

    String restBaseUrl = resolveSupabaseRestBaseUrl(supabaseUrl);
    String deleteUrl = restBaseUrl + "/storage/v1/object/" + supabaseBucket + "/" + objectName.get();

    HttpClient client = HttpClient.newHttpClient();
    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(deleteUrl))
        .header("apikey", supabaseApiKey)
        .header("Authorization", "Bearer " + supabaseApiKey)
        .DELETE()
        .build();

    try {
      HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
      // Storage returns 200/204 when deleted; 404 if already gone.
      if (res.statusCode() == 404) {
        log.info("Supabase storage object already missing: bucket={}, path={}", supabaseBucket, objectName.get());
        return;
      }
      if (res.statusCode() < 200 || res.statusCode() >= 300) {
        log.warn("Supabase storage delete failed: status={}, bucket={}, path={}, body={}",
            res.statusCode(), supabaseBucket, objectName.get(), truncateForLog(res.body()));
      }
    } catch (IOException | InterruptedException e) {
      log.warn("Supabase storage delete error: bucket={}, path={}, msg={}", supabaseBucket, objectName.get(), e.getMessage());
    }
  }

  private Optional<String> extractObjectNameFromPublicUrl(String publicUrl) {
    if (publicUrl == null || publicUrl.isBlank()) return Optional.empty();

    String restBaseUrl = resolveSupabaseRestBaseUrl(supabaseUrl);
    String prefix = restBaseUrl + "/storage/v1/object/public/" + supabaseBucket + "/";

    String u = publicUrl.trim();
    if (u.startsWith(prefix)) {
      return Optional.of(u.substring(prefix.length()));
    }

    // handle URLs where base differs but path format matches
    String marker = "/storage/v1/object/public/" + supabaseBucket + "/";
    int idx = u.indexOf(marker);
    if (idx >= 0) {
      return Optional.of(u.substring(idx + marker.length()));
    }

    return Optional.empty();
  }

  private static String truncateForLog(String s) {
    if (s == null) return "";
    int max = 2000;
    if (s.length() <= max) return s;
    return s.substring(0, max) + "... (truncated)";
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


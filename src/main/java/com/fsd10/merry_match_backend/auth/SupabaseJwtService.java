package com.fsd10.merry_match_backend.auth;

import com.fsd10.merry_match_backend.config.JwtProperties;
import com.fsd10.merry_match_backend.config.SupabaseProperties;
import com.fsd10.merry_match_backend.entity.User;
import com.fsd10.merry_match_backend.repository.UserRepository;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityNotFoundException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SupabaseJwtService {

  private final JwtProperties jwtProperties;
  private final SupabaseProperties supabaseProperties;
  private final UserRepository userRepository;

  /**
   * Verifies a Supabase access token and resolves the application user id.
   * Supports HS256 (legacy JWT secret) and ES256 (JWKS from {@code supabase.url}).
   */
  public UUID requireUserIdFromAuthorization(String authorizationHeader) {
    String token = stripBearerPrefix(authorizationHeader);
    SignedJWT signedJwt;
    try {
      signedJwt = SignedJWT.parse(token);
    } catch (ParseException e) {
      throw new IllegalArgumentException("Invalid JWT format", e);
    }

    JWSAlgorithm alg = signedJwt.getHeader().getAlgorithm();
    if (JWSAlgorithm.HS256.equals(alg)) {
      return verifyHs256AndResolveUserId(token);
    }
    if (JWSAlgorithm.ES256.equals(alg)) {
      return verifyEs256AndResolveUserId(token);
    }
    throw new IllegalArgumentException("Unsupported JWT algorithm: " + alg);
  }

  private UUID verifyHs256AndResolveUserId(String token) {
    String secret = jwtProperties.secret();
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("jwt.secret is not configured; set JWT_SECRET for HS256 tokens");
    }

    SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    Claims claims;
    try {
      claims = Jwts.parser()
          .verifyWith(key)
          .build()
          .parseSignedClaims(token)
          .getPayload();
    } catch (JwtException e) {
      throw new IllegalArgumentException("Invalid or expired access token", e);
    }

    validateAudienceIfConfigured(claims);
    return resolveUserIdFromJjwtClaims(claims);
  }

  private UUID verifyEs256AndResolveUserId(String token) {
    String base = resolveSupabaseRestBaseUrl(supabaseProperties.url());
    String jwksUrl = base + "/auth/v1/.well-known/jwks.json";
    URL jwks;
    try {
      jwks = URI.create(jwksUrl).toURL();
    } catch (MalformedURLException e) {
      throw new IllegalStateException("Invalid JWKS URL: " + jwksUrl, e);
    }

    ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
    try {
      @SuppressWarnings("deprecation")
      JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(jwks);
      processor.setJWSKeySelector(new JWSVerificationKeySelector<>(Set.of(JWSAlgorithm.ES256), keySource));
      JWTClaimsSet claims = processor.process(token, null);
      String expectedIss = base + "/auth/v1";
      String iss = claims.getIssuer();
      if (iss == null || !iss.equals(expectedIss)) {
        throw new IllegalArgumentException("Invalid token issuer");
      }
      validateAudienceIfConfigured(claims);
      return resolveUserIdFromNimbusClaims(claims);
    } catch (ParseException | BadJOSEException | JOSEException e) {
      throw new IllegalArgumentException("Invalid or expired access token", e);
    }
  }

  private UUID resolveUserIdFromJjwtClaims(Claims claims) {
    UUID fromSub = parseUuid(claims.getSubject());
    if (fromSub != null) {
      return fromSub;
    }
    UUID fromUserId = parseUuid(claims.get("user_id", String.class));
    if (fromUserId != null) {
      return fromUserId;
    }
    UUID fromId = parseUuid(claims.get("id", String.class));
    if (fromId != null) {
      return fromId;
    }
    String email = claims.get("email", String.class);
    return userIdFromEmail(email);
  }

  private UUID resolveUserIdFromNimbusClaims(JWTClaimsSet claims) {
    UUID fromSub = parseUuid(claims.getSubject());
    if (fromSub != null) {
      return fromSub;
    }
    UUID fromUserId = parseUuid(stringClaim(claims, "user_id"));
    if (fromUserId != null) {
      return fromUserId;
    }
    UUID fromId = parseUuid(stringClaim(claims, "id"));
    if (fromId != null) {
      return fromId;
    }
    return userIdFromEmail(stringClaim(claims, "email"));
  }

  /** Avoids {@link JWTClaimsSet#getStringClaim} which may throw {@link ParseException} for non-string JSON types. */
  private static String stringClaim(JWTClaimsSet claims, String name) {
    Object v = claims.getClaim(name);
    if (v == null) {
      return null;
    }
    if (v instanceof String s) {
      return s;
    }
    return null;
  }

  private UUID userIdFromEmail(String email) {
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("Cannot resolve user id from access token claims");
    }
    User user = userRepository.findByEmail(email.toLowerCase().trim())
        .orElseThrow(() -> new EntityNotFoundException("User not found for email: " + email));
    return user.getId();
  }

  private void validateAudienceIfConfigured(Claims claims) {
    String expected = jwtProperties.audience();
    if (expected == null || expected.isBlank()) {
      return;
    }
    Object aud = claims.get("aud");
    if (aud instanceof String s) {
      if (!expected.equals(s)) {
        throw new IllegalArgumentException("Invalid token audience");
      }
      return;
    }
    if (aud instanceof Collection<?> c) {
      boolean ok = c.stream().anyMatch(o -> expected.equals(String.valueOf(o)));
      if (!ok) {
        throw new IllegalArgumentException("Invalid token audience");
      }
      return;
    }
    throw new IllegalArgumentException("Invalid token audience");
  }

  private void validateAudienceIfConfigured(JWTClaimsSet claims) {
    String expected = jwtProperties.audience();
    if (expected == null || expected.isBlank()) {
      return;
    }
    List<String> aud = claims.getAudience();
    if (aud != null && aud.contains(expected)) {
      return;
    }
    throw new IllegalArgumentException("Invalid token audience");
  }

  private static String stripBearerPrefix(String authorizationHeader) {
    if (authorizationHeader == null || authorizationHeader.isBlank()) {
      throw new IllegalArgumentException("Missing Authorization header");
    }
    String t = authorizationHeader.trim();
    if (t.toLowerCase().startsWith("bearer ")) {
      t = t.substring(7).trim();
    }
    if (t.isBlank()) {
      throw new IllegalArgumentException("Missing bearer token");
    }
    return t;
  }

  private static UUID parseUuid(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static String resolveSupabaseRestBaseUrl(String url) {
    if (url == null || url.isBlank()) {
      throw new IllegalStateException("supabase.url is not configured; required for ES256 (JWKS) verification");
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
}

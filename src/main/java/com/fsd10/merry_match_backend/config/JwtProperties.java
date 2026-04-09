package com.fsd10.merry_match_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
		String secret,
		Long expiration,
		/** When set, access token {@code aud} must match (typical Supabase value: {@code authenticated}). */
		String audience
) {
}

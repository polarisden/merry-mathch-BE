package com.fsd10.merry_match_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "supabase")
public record SupabaseProperties(
		String url,
		String bucket,
		/**
		 * Service role key used for server-to-server calls (storage sign URL, admin ops).
		 * Do not expose to frontend.
		 */
		String apiKey,
		/** Signed URL TTL for chat images (seconds). */
		Long chatImageSignTtlSeconds
) {
}

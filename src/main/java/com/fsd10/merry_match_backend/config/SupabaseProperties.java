package com.fsd10.merry_match_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "supabase")
public record SupabaseProperties(
		String url,
		String bucket,
		String apiKey
) {
}

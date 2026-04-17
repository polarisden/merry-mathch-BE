package com.fsd10.merry_match_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omise")
public record OmiseProperties(
    String publicKey,
    String secretKey,
    /**
     * Base64 webhook secret from Omise Dashboard (Webhooks settings). Used to verify
     * {@code Omise-Signature} on incoming webhooks.
     */
    String webhookSecret
) {}

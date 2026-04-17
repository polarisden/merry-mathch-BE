package com.fsd10.merry_match_backend.payment;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.fsd10.merry_match_backend.config.OmiseProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Verifies Omise webhook authenticity using {@code Omise-Signature} and
 * {@code Omise-Signature-Timestamp} per Omise documentation (HMAC-SHA256 over
 * {@code timestamp + "." + rawBody} with Base64-decoded webhook secret).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OmiseWebhookSignatureVerifier {

    private final OmiseProperties omiseProperties;

    /**
     * @return true if verification is skipped (no secret configured), or signature matches
     */
    public boolean verifyOrSkip(String signatureHeader, String timestampHeader, byte[] rawBody) {
        String secretB64 = omiseProperties.webhookSecret();
        if (secretB64 == null || secretB64.isBlank()) {
            log.warn("omise.webhook-secret is empty; webhook signature verification is skipped (set OMISE_WEBHOOK_SECRET for production)");
            return true;
        }
        if (signatureHeader == null || signatureHeader.isBlank() || timestampHeader == null || timestampHeader.isBlank()) {
            return false;
        }
        byte[] secretBytes;
        try {
            secretBytes = Base64.getDecoder().decode(secretB64.trim());
        } catch (IllegalArgumentException e) {
            log.error("Invalid Base64 omise.webhook-secret");
            return false;
        }
        String signedPayload = timestampHeader + "." + new String(rawBody, StandardCharsets.UTF_8);
        String expectedHex;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
            byte[] digest = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            expectedHex = toHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("HMAC init failed", e);
            return false;
        }
        return Arrays.stream(signatureHeader.split(","))
                .map(String::trim)
                .anyMatch(sig -> constantTimeEquals(sig, expectedHex));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }
}

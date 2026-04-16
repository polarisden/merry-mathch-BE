package com.fsd10.merry_match_backend.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd10.merry_match_backend.exception.SubscriptionAlreadyActiveException;
import com.fsd10.merry_match_backend.payment.OmiseWebhookSignatureVerifier;
import com.fsd10.merry_match_backend.service.OmiseSubscriptionService;

import co.omise.models.OmiseException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Configure this URL in Omise Dashboard (test/live) Webhooks.
 * Local: use ngrok HTTPS URL, e.g. {@code https://&lt;your-id&gt;.ngrok-free.dev/api/webhooks/omise}.
 * Production (Render): set the public service URL + {@code /api/webhooks/omise}.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class OmiseWebhookController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OmiseWebhookSignatureVerifier signatureVerifier;
    private final OmiseSubscriptionService omiseSubscriptionService;

    @PostMapping(path = "/api/webhooks/omise", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> handle(
            @RequestHeader(value = "Omise-Signature", required = false) String signature,
            @RequestHeader(value = "Omise-Signature-Timestamp", required = false) String timestamp,
            @RequestBody byte[] rawBody
    ) throws java.io.IOException {
        if (!signatureVerifier.verifyOrSkip(signature, timestamp, rawBody)) {
            return ResponseEntity.status(401).build();
        }

        JsonNode root = OBJECT_MAPPER.readTree(rawBody);
        String key = root.path("key").asText("");
        if (!key.startsWith("charge.")) {
            return ResponseEntity.ok().build();
        }

        JsonNode data = root.get("data");
        if (data == null || !"charge".equals(data.path("object").asText())) {
            return ResponseEntity.ok().build();
        }

        String chargeId = data.path("id").asText(null);
        if (chargeId == null || chargeId.isBlank()) {
            log.warn("Webhook charge event without id: key={}", key);
            return ResponseEntity.ok().build();
        }

        try {
            omiseSubscriptionService.syncChargeFromWebhook(chargeId);
        } catch (SubscriptionAlreadyActiveException e) {
            log.info("Webhook charge {} already fulfilled (idempotent): {}", chargeId, e.getMessage());
        } catch (EntityNotFoundException | IllegalArgumentException e) {
            log.warn("Webhook charge {} skipped: {}", chargeId, e.getMessage());
        } catch (OmiseException e) {
            log.error("Omise API error while handling webhook for charge {}", chargeId, e);
            return ResponseEntity.internalServerError().build();
        }
        return ResponseEntity.ok().build();
    }
}

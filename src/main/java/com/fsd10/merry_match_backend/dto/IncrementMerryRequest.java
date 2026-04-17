package com.fsd10.merry_match_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record IncrementMerryRequest(
        @JsonProperty("user_id") UUID userId
) {}

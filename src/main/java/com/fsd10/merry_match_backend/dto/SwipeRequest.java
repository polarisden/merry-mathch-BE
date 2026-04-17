package com.fsd10.merry_match_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record SwipeRequest(
        @JsonProperty("swiper_id") UUID swiperId,
        @JsonProperty("swiped_id") UUID swipedId,
        String action
) {}

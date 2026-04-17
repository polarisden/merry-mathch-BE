package com.fsd10.merry_match_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record SwipeHistoryResponse(
        @JsonProperty("list_swiped") List<UUID> listSwiped,
        @JsonProperty("list_swiped_like") List<UUID> listSwipedLike,
        @JsonProperty("list_swiped_pass") List<UUID> listSwipedPass
) {}

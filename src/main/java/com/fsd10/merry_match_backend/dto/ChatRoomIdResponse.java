package com.fsd10.merry_match_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record ChatRoomIdResponse(
        @JsonProperty("chatroom_ids") List<UUID> chatroomIds
) {}

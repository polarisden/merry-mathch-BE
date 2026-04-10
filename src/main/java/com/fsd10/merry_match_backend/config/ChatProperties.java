package com.fsd10.merry_match_backend.config;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "merry.chat")
public record ChatProperties(
    boolean seedDevRoom,
    UUID devRoomId,
    String devRoomParticipantEmail
) {}

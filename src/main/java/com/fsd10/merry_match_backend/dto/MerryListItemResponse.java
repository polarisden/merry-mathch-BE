package com.fsd10.merry_match_backend.dto;

import java.util.UUID;

public record MerryListItemResponse(
    UUID id,
    String name,
    Integer age,
    String location,
    String img,
    String matchStatus,
    boolean matched,
    boolean merryToday,
    String sexualIdentity,
    String sexualPreference,
    String racialPreference,
    String meetingInterest
) {
}

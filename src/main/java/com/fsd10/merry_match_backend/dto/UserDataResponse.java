package com.fsd10.merry_match_backend.dto;

import java.util.UUID;

public record UserDataResponse(
        UUID id,
        String name,
        Integer age,
        String locationCity,
        String locationCountry,
        String mainImage,
        String gender,
        String sexualPreference
) {}

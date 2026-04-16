package com.fsd10.merry_match_backend.dto;

import java.util.List;

public record MerryListResponse(
    List<MerryListItemResponse> items,
    int merryToYou,
    int merryMatch,
    int limitUsed,
    int limitMax
) {
}

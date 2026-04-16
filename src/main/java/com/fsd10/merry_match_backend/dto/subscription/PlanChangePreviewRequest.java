package com.fsd10.merry_match_backend.dto.subscription;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record PlanChangePreviewRequest(@NotNull UUID planId) {}

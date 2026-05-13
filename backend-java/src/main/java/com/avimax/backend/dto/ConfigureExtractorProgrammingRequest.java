package com.avimax.backend.dto;

import jakarta.validation.constraints.NotNull;

public record ConfigureExtractorProgrammingRequest(
        @NotNull Double temperatureOn,
        @NotNull Double temperatureOff
) {
}

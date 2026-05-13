package com.avimax.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateMortalityRequest(
        @NotNull @Min(0) Integer maleCount,
        @NotNull @Min(0) Integer femaleCount,
        String observations
) {
}

package com.avimax.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ConfigureBombaProgrammingRequest(
        @NotNull Double temperatureOn,
        @NotNull Double temperatureOff,
        @NotNull @Min(1) Integer workDurationSeconds
) {
}

package com.avimax.backend.dto;

import jakarta.validation.constraints.NotNull;

public record ConfigureCriadoraProgrammingRequest(
        @NotNull Double temperatureOn,
        @NotNull Double temperatureOff
) {
}

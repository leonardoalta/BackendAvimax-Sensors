package com.avimax.sensors.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTemperatureReadingRequest(
        @NotNull Double value,
        @NotBlank String location
) {
}

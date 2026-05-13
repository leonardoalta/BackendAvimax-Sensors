package com.avimax.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateFlockRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @Min(1) Integer totalBirds,
        @NotNull @Min(0) Integer maleCount,
        @NotNull @Min(0) Integer femaleCount,
        @NotNull LocalDate flockDate,
        @NotBlank @Size(max = 80) String birdLot,
        @Size(max = 500) String notes
) {
}

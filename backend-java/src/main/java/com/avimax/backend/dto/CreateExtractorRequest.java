package com.avimax.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateExtractorRequest(
        @NotBlank @Size(max = 100) String name
) {
}

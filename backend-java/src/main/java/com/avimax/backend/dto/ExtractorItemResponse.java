package com.avimax.backend.dto;

import com.avimax.backend.entity.Extractor;
import com.avimax.backend.entity.ExtractorProgramming;
import java.time.OffsetDateTime;

public record ExtractorItemResponse(
        Long id,
        String name,
        boolean enabled,
        OffsetDateTime createdAt,
        Double temperatureOn,
        Double temperatureOff,
        OffsetDateTime programmingUpdatedAt
) {
    public static ExtractorItemResponse from(Extractor extractor, ExtractorProgramming programming) {
        return new ExtractorItemResponse(
                extractor.getId(),
                extractor.getName(),
                extractor.isEnabled(),
                extractor.getCreatedAt(),
                programming != null ? programming.getTemperatureOn() : null,
                programming != null ? programming.getTemperatureOff() : null,
                programming != null ? programming.getUpdatedAt() : null
        );
    }
}

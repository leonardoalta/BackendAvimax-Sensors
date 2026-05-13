package com.avimax.backend.dto;

import java.time.OffsetDateTime;

public record ExtractorHistoryResponse(
        Long id,
        Long actuatorId,
        String actuatorName,
        String actuatorType,
        Double temperatureOn,
        Double temperatureOff,
        OffsetDateTime recordedAt
) {
    public static ExtractorHistoryResponse of(Long id, Long actuatorId, String actuatorName, String actuatorType, Double temperatureOn, Double temperatureOff, OffsetDateTime recordedAt) {
        return new ExtractorHistoryResponse(id, actuatorId, actuatorName, actuatorType, temperatureOn, temperatureOff, recordedAt);
    }
}

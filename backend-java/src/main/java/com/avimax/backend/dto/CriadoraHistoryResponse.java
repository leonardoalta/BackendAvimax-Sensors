package com.avimax.backend.dto;

import java.time.OffsetDateTime;

public record CriadoraHistoryResponse(
        Long id,
        Long actuatorId,
        String actuatorName,
        String actuatorType,
        Double temperatureOn,
        Double temperatureOff,
        OffsetDateTime recordedAt
) {
    public static CriadoraHistoryResponse of(Long id, Long actuatorId, String actuatorName, String actuatorType, Double temperatureOn, Double temperatureOff, OffsetDateTime recordedAt) {
        return new CriadoraHistoryResponse(id, actuatorId, actuatorName, actuatorType, temperatureOn, temperatureOff, recordedAt);
    }
}

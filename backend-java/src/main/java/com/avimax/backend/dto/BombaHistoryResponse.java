package com.avimax.backend.dto;

import java.time.OffsetDateTime;

public record BombaHistoryResponse(
        Long id,
        Long actuatorId,
        String actuatorName,
        String actuatorType,
        Double temperatureOn,
        Double temperatureOff,
        Integer workDurationSeconds,
        OffsetDateTime recordedAt
) {
    public static BombaHistoryResponse of(Long id, Long actuatorId, String actuatorName, String actuatorType, Double temperatureOn, Double temperatureOff, Integer workDurationSeconds, OffsetDateTime recordedAt) {
        return new BombaHistoryResponse(id, actuatorId, actuatorName, actuatorType, temperatureOn, temperatureOff, workDurationSeconds, recordedAt);
    }
}

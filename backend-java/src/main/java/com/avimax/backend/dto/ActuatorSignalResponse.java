package com.avimax.backend.dto;

import java.time.OffsetDateTime;

public record ActuatorSignalResponse(
        Long commandId,
        String actuatorType,
        Long actuatorId,
        String actuatorName,
        String command,
        Integer workDurationSeconds,
        String reason,
        OffsetDateTime createdAt
) {
    public static ActuatorSignalResponse of(Long commandId, String actuatorType, Long actuatorId, String actuatorName, String command, Integer workDurationSeconds, String reason, OffsetDateTime createdAt) {
        return new ActuatorSignalResponse(commandId, actuatorType, actuatorId, actuatorName, command, workDurationSeconds, reason, createdAt);
    }
}

package com.avimax.backend.dto;

import java.time.OffsetDateTime;

public record LocalManualActuatorCommandResponse(
        String actuatorType,
        Long actuatorId,
        String actuatorName,
        String action,
        boolean state,
        String reason,
        String triggeredBy,
        Integer workDurationSeconds,
        OffsetDateTime changedAt
) {}

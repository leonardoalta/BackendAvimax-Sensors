package com.avimax.backend.dto;

import com.avimax.backend.entity.Bomba;
import com.avimax.backend.entity.BombaProgramming;
import java.time.OffsetDateTime;

public record BombaItemResponse(
        Long id,
        String name,
        boolean enabled,
        OffsetDateTime createdAt,
        Double temperatureOn,
        Double temperatureOff,
        Integer workDurationSeconds,
        OffsetDateTime programmingUpdatedAt
) {
    public static BombaItemResponse from(Bomba bomba, BombaProgramming programming) {
        return new BombaItemResponse(
                bomba.getId(),
                bomba.getName(),
                bomba.isEnabled(),
                bomba.getCreatedAt(),
                programming != null ? programming.getTemperatureOn() : null,
                programming != null ? programming.getTemperatureOff() : null,
                programming != null ? programming.getWorkDurationSeconds() : null,
                programming != null ? programming.getUpdatedAt() : null
        );
    }
}

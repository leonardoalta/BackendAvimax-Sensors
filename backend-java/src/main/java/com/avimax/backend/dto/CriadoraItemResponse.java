package com.avimax.backend.dto;

import com.avimax.backend.entity.Criadora;
import com.avimax.backend.entity.CriadoraProgramming;
import java.time.OffsetDateTime;

public record CriadoraItemResponse(
        Long id,
        String name,
        String codeName,
        boolean enabled,
        OffsetDateTime createdAt,
        Double temperatureOn,
        Double temperatureOff,
        OffsetDateTime programmingUpdatedAt
) {
    public static CriadoraItemResponse from(Criadora criadora, CriadoraProgramming programming) {
        return new CriadoraItemResponse(
                criadora.getId(),
                criadora.getName(),
                criadora.getCodeName(),
                criadora.isEnabled(),
                criadora.getCreatedAt(),
                programming != null ? programming.getTemperatureOn() : null,
                programming != null ? programming.getTemperatureOff() : null,
                programming != null ? programming.getUpdatedAt() : null
        );
    }
}

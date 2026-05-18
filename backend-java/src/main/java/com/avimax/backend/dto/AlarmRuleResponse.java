package com.avimax.backend.dto;

import com.avimax.backend.entity.AlarmRule;
import java.time.OffsetDateTime;

public record AlarmRuleResponse(
        Long idRegla,
        String nombre,
        String variableMonitoreada,
        String condicion,
        Double umbral,
        String unidad,
        Integer tiempoMinimoSegundos,
        String severidad,
        String mensaje,
        boolean activa,
        OffsetDateTime fechaCreacion,
        OffsetDateTime fechaActualizacion
) {
    public static AlarmRuleResponse fromEntity(AlarmRule rule) {
        return new AlarmRuleResponse(
                rule.getId(),
                rule.getName(),
                rule.getVariable().name().toLowerCase(),
                rule.getConditionType().name().toLowerCase(),
                rule.getThreshold(),
                rule.getUnit(),
                rule.getMinimumDurationSeconds(),
                rule.getSeverity().name().toLowerCase(),
                rule.getMessage(),
                rule.isActive(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }
}

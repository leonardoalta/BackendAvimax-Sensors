package com.avimax.backend.dto;

import com.avimax.backend.entity.Alarm;
import java.time.OffsetDateTime;

public record AlarmResponse(
        Long idAlarma,
        Long idRegla,
        String nombreRegla,
        String variable,
        Double valorDetectado,
        Double umbral,
        String unidad,
        String condicion,
        String severidad,
        String mensaje,
        String estado,
        OffsetDateTime fechaActivacion,
        OffsetDateTime fechaReconocimiento,
        OffsetDateTime fechaResolucion,
        OffsetDateTime fechaCierre
) {
    public static AlarmResponse fromEntity(Alarm alarm) {
        return new AlarmResponse(
                alarm.getId(),
                alarm.getRule().getId(),
                alarm.getRuleName(),
                alarm.getVariable().name().toLowerCase(),
                alarm.getDetectedValue(),
                alarm.getThreshold(),
                alarm.getUnit(),
                alarm.getConditionType().name().toLowerCase(),
                alarm.getSeverity().name().toLowerCase(),
                alarm.getMessage(),
                alarm.getStatus().name().toLowerCase(),
                alarm.getActivatedAt(),
                alarm.getAcknowledgedAt(),
                alarm.getResolvedAt(),
                alarm.getClosedAt()
        );
    }
}

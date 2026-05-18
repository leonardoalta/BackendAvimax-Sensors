package com.avimax.backend.dto;

import com.avimax.backend.entity.AlarmCondition;
import com.avimax.backend.entity.AlarmSeverity;
import com.avimax.backend.entity.AlarmVariable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAlarmRuleRequest(
        @NotBlank @Size(max = 120) String nombre,
        @NotNull AlarmVariable variable,
        @NotNull AlarmCondition condicion,
        @NotNull Double umbral,
        @NotBlank @Size(max = 10) String unidad,
        @NotNull @Min(0) Integer tiempoMinimoSegundos,
        @NotNull AlarmSeverity severidad,
        @NotBlank @Size(max = 500) String mensaje,
        @NotNull Boolean activa
) {
}

package com.avimax.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record DashboardPrincipalResponse(
        @JsonProperty("data") DashboardPrincipalData data,
        @JsonProperty("meta") DashboardMeta meta
) {
    public static DashboardPrincipalResponse of(DashboardPrincipalData data, DashboardMeta meta) {
        return new DashboardPrincipalResponse(data, meta);
    }

    public record DashboardPrincipalData(
            @JsonProperty("galpon_id") Long galponId,
            @JsonProperty("parvada") ParvadaDashboardData parvada,
            @JsonProperty("peso_actual") PesoActualDashboardData pesoActual,
            @JsonProperty("telemetria_actual") TelemetriaActualDashboardData telemetriaActual,
            @JsonProperty("telemetria_min_max_dia") TelemetriaMinMaxDiaDashboardData telemetriaMinMaxDia
    ) {
    }

    public record ParvadaDashboardData(
            @JsonProperty("parvada_id") Long parvadaId,
            @JsonProperty("fecha_ingreso") LocalDate fechaIngreso,
            @JsonProperty("edad_dias") Integer edadDias,
            @JsonProperty("aves_vivas") Integer avesVivas
    ) {
    }

    public record PesoActualDashboardData(
            @JsonProperty("fecha_registro") LocalDate fechaRegistro,
            @JsonProperty("peso_promedio_kg") Double pesoPromedioKg
    ) {
    }

    public record TelemetriaActualDashboardData(
            @JsonProperty("event_time") OffsetDateTime eventTime,
            @JsonProperty("temperatura_c") Double temperaturaC,
            @JsonProperty("humedad_relativa") Double humedadRelativa,
            @JsonProperty("nh3_ppm") Double nh3Ppm
    ) {
    }

    public record TelemetriaMinMaxDiaDashboardData(
            @JsonProperty("temperatura_c") MinMax temperaturaC,
            @JsonProperty("humedad_relativa") MinMax humedadRelativa,
            @JsonProperty("nh3_ppm") MinMax nh3Ppm
    ) {
    }

    public record MinMax(
            @JsonProperty("min") Double min,
            @JsonProperty("max") Double max
    ) {
    }

    public record DashboardMeta(
            @JsonProperty("generated_at") OffsetDateTime generatedAt,
            @JsonProperty("status") String status
    ) {
    }
}
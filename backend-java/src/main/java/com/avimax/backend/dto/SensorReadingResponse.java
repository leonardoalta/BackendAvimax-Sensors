package com.avimax.backend.dto;

import com.avimax.backend.entity.SensorReading;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record SensorReadingResponse(
        Long id,
        Long flockId,
        String gatewayId,
        String sensor,
        String deviceId,
        OffsetDateTime timestamp,
        @JsonProperty("temperatura_c")
        Double temperatureC,
        @JsonProperty("humedad_relativa")
        Double humidityPercent,
        @JsonProperty("nh3_ppm")
        Double nh3Ppm
) {
    public static SensorReadingResponse fromEntity(SensorReading reading) {
        return new SensorReadingResponse(
                reading.getId(),
                reading.getFlock().getId(),
                reading.getGatewayId(),
                reading.getSourceTopic(),  // sensor field
                reading.getSourceTopic(),  // deviceId (aprox sensor)
                reading.getRecordedAt(),   // timestamp
                reading.getTemperatureC(),
                reading.getHumidityPercent(),
                reading.getNh3Ppm()
        );
    }
}

package com.avimax.backend.dto;

import com.avimax.backend.entity.SensorReading;
import java.time.OffsetDateTime;

public record SensorReadingResponse(
        Long id,
        Long flockId,
        String gatewayId,
        String sourceTopic,
        OffsetDateTime recordedAt,
        Double temperatureC,
        Double humidityPercent,
        Double nh3Ppm
) {
    public static SensorReadingResponse fromEntity(SensorReading reading) {
        return new SensorReadingResponse(
                reading.getId(),
                reading.getFlock().getId(),
                reading.getGatewayId(),
                reading.getSourceTopic(),
                reading.getRecordedAt(),
                reading.getTemperatureC(),
                reading.getHumidityPercent(),
                reading.getNh3Ppm()
        );
    }
}

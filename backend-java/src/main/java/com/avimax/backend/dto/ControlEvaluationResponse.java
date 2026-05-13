package com.avimax.backend.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ControlEvaluationResponse(
        OffsetDateTime evaluatedAt,
        Double temperatureC,
        Double humidityPercent,
        Double nh3Ppm,
        ControlCountsResponse counts,
        List<ActuatorSignalResponse> signals
) {
}

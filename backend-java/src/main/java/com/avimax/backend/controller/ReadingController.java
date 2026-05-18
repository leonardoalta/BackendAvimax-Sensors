package com.avimax.backend.controller;

import com.avimax.backend.dto.ApiResponse;
import com.avimax.backend.dto.SensorReadingResponse;
import com.avimax.backend.service.SensorReadingService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/readings")
public class ReadingController {

    private final SensorReadingService sensorReadingService;

    public ReadingController(SensorReadingService sensorReadingService) {
        this.sensorReadingService = sensorReadingService;
    }

    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<SensorReadingResponse>> latest() {
        return sensorReadingService.getLatestReading()
                .map(SensorReadingResponse::fromEntity)
                .map(ApiResponse::success)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/recent")
    public ApiResponse<List<SensorReadingResponse>> recent() {
        List<SensorReadingResponse> readings = sensorReadingService.getRecentReadings()
                .stream()
                .map(SensorReadingResponse::fromEntity)
                .toList();
        return ApiResponse.success(readings);
    }
}

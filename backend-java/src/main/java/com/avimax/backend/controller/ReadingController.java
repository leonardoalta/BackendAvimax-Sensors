package com.avimax.backend.controller;

import com.avimax.backend.dto.ApiResponse;
import com.avimax.backend.dto.SensorReadingPageResponse;
import com.avimax.backend.dto.SensorReadingResponse;
import com.avimax.backend.service.SensorReadingService;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/readings")
public class ReadingController {

    private final SensorReadingService sensorReadingService;

    public ReadingController(SensorReadingService sensorReadingService) {
        this.sensorReadingService = sensorReadingService;
    }

    @GetMapping
    public ApiResponse<SensorReadingPageResponse> getReadings(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String variable,
            @RequestParam(required = false) String gateway,
            @RequestParam(required = false) String sensor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String sort) {

        // Parse ISO-8601 timestamps
        OffsetDateTime startDt = null;
        OffsetDateTime endDt = null;
        if (start != null && !start.isEmpty()) {
            startDt = OffsetDateTime.parse(start, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
        if (end != null && !end.isEmpty()) {
            endDt = OffsetDateTime.parse(end, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }

        // Query with filters
        var pageResult = sensorReadingService.getReadingsWithFilters(
                startDt, endDt, variable, gateway, sensor, page, size, sort);

        // Convert to DTOs
        List<SensorReadingResponse> dtos = pageResult.getContent()
                .stream()
                .map(SensorReadingResponse::fromEntity)
                .toList();

        // Build response with pagination metadata
        SensorReadingPageResponse respData = new SensorReadingPageResponse(
                dtos,
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements());

        return ApiResponse.success(respData);
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

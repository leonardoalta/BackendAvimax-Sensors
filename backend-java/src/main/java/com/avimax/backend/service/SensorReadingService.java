package com.avimax.backend.service;

import com.avimax.backend.entity.Flock;
import com.avimax.backend.entity.SensorReading;
import com.avimax.backend.repository.SensorReadingRepository;
import com.avimax.backend.repository.SensorReadingSpecification;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SensorReadingService {

    private static final Logger log = LoggerFactory.getLogger(SensorReadingService.class);

    private final SensorReadingRepository sensorReadingRepository;
    private final FlockService flockService;
    private final ActuatorControlService actuatorControlService;
    private final AlarmEvaluationService alarmEvaluationService;

    public SensorReadingService(SensorReadingRepository sensorReadingRepository,
                                FlockService flockService,
                                ActuatorControlService actuatorControlService,
                                AlarmEvaluationService alarmEvaluationService) {
        this.sensorReadingRepository = sensorReadingRepository;
        this.flockService = flockService;
        this.actuatorControlService = actuatorControlService;
        this.alarmEvaluationService = alarmEvaluationService;
    }

    @Transactional
    public Optional<SensorReading> saveIfActiveFlock(String gatewayId, String sourceTopic, OffsetDateTime recordedAt, Double temperatureC, Double humidityPercent, Double nh3Ppm) {
        Optional<Flock> activeFlock = flockService.getActiveFlock();
        if (activeFlock.isEmpty()) {
            log.warn("Lectura recibida pero no existe una parvada activa. Se omite el guardado.");
            return Optional.empty();
        }

        SensorReading reading = new SensorReading(
                activeFlock.get(),
                recordedAt,
                gatewayId,
                sourceTopic,
                temperatureC,
                humidityPercent,
                nh3Ppm
        );

        SensorReading saved = sensorReadingRepository.save(reading);
        alarmEvaluationService.evaluate(saved);
        actuatorControlService.evaluateAndQueue(saved);
        return Optional.of(saved);
    }

    @Transactional(readOnly = true)
    public Optional<SensorReading> getLatestReading() {
        return sensorReadingRepository.findTopByOrderByRecordedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<SensorReading> getRecentReadings() {
        return sensorReadingRepository.findTop20ByOrderByRecordedAtDesc();
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<SensorReading> getReadingsWithFilters(
            OffsetDateTime start,
            OffsetDateTime end,
            String variable,
            String gateway,
            String sensor,
            int page,
            int size,
            String sort) {

        // Default values
        if (page < 0) page = 0;
        if (size <= 0) size = 100;
        if (size > 1000) size = 1000;  // Cap para evitar queries enormes

        // Parse sort order (formato: "field,asc" o "field,desc")
        Sort.Direction direction = Sort.Direction.DESC;
        String sortField = "recordedAt";
        if (sort != null && !sort.isEmpty()) {
            String[] parts = sort.split(",");
            if (parts.length == 2) {
                sortField = parts[0];
                direction = "asc".equalsIgnoreCase(parts[1]) ? Sort.Direction.ASC : Sort.Direction.DESC;
            }
        }

        var pageable = PageRequest.of(page, size, Sort.by(direction, sortField));
        var spec = SensorReadingSpecification.withFilters(start, end, variable, gateway, sensor);

        return sensorReadingRepository.findAll(spec, pageable);
    }
}

package com.avimax.backend.service;

import com.avimax.backend.entity.Flock;
import com.avimax.backend.entity.SensorReading;
import com.avimax.backend.repository.SensorReadingRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
}

package com.avimax.backend.service;

import com.avimax.backend.dto.DashboardPrincipalResponse;
import com.avimax.backend.entity.Flock;
import com.avimax.backend.entity.FlockStatus;
import com.avimax.backend.entity.SensorReading;
import com.avimax.backend.entity.WeightRecord;
import com.avimax.backend.repository.FlockRepository;
import com.avimax.backend.repository.SensorReadingRepository;
import com.avimax.backend.repository.WeightRecordRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DashboardService {

    private final FlockRepository flockRepository;
    private final WeightRecordRepository weightRecordRepository;
    private final SensorReadingRepository sensorReadingRepository;

    public DashboardService(FlockRepository flockRepository,
                            WeightRecordRepository weightRecordRepository,
                            SensorReadingRepository sensorReadingRepository) {
        this.flockRepository = flockRepository;
        this.weightRecordRepository = weightRecordRepository;
        this.sensorReadingRepository = sensorReadingRepository;
    }

    public DashboardPrincipalResponse getPrincipalDashboard() {
        Flock flock = flockRepository.findFirstByStatus(FlockStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay parvada activa"));

        Long flockId = flock.getId();
        LocalDate today = LocalDate.now();
        ZoneId zoneId = ZoneId.systemDefault();
        OffsetDateTime startOfDay = today.atStartOfDay(zoneId).toOffsetDateTime();
        OffsetDateTime startOfNextDay = today.plusDays(1).atStartOfDay(zoneId).toOffsetDateTime();

        DashboardPrincipalResponse.ParvadaDashboardData parvada = new DashboardPrincipalResponse.ParvadaDashboardData(
                flockId,
                flock.getFlockDate(),
                Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(flock.getFlockDate(), today)),
                flock.getTotalBirds()
        );

        DashboardPrincipalResponse.PesoActualDashboardData pesoActual = weightRecordRepository
                .findFirstByFlockIdOrderByRecordDateDescCreatedAtDesc(flockId)
                .map(this::toPesoActual)
                .orElse(null);

        DashboardPrincipalResponse.TelemetriaActualDashboardData telemetriaActual = sensorReadingRepository
                .findFirstByFlockIdOrderByRecordedAtDesc(flockId)
                .map(this::toTelemetriaActual)
                .orElse(null);

        List<SensorReading> readings = sensorReadingRepository
                .findByFlockIdAndRecordedAtBetweenOrderByRecordedAtDesc(flockId, startOfDay, startOfNextDay);

        DashboardPrincipalResponse.TelemetriaMinMaxDiaDashboardData telemetriaMinMaxDia = buildMinMax(readings);

        DashboardPrincipalResponse.DashboardPrincipalData data = new DashboardPrincipalResponse.DashboardPrincipalData(
                flockId,
                parvada,
                pesoActual,
                telemetriaActual,
                telemetriaMinMaxDia
        );

        DashboardPrincipalResponse.DashboardMeta meta = new DashboardPrincipalResponse.DashboardMeta(
                OffsetDateTime.now(),
                "ok"
        );

        return DashboardPrincipalResponse.of(data, meta);
    }

    private DashboardPrincipalResponse.PesoActualDashboardData toPesoActual(WeightRecord record) {
        return new DashboardPrincipalResponse.PesoActualDashboardData(
                record.getRecordDate(),
                record.getAverageWeight() / 1000.0
        );
    }

    private DashboardPrincipalResponse.TelemetriaActualDashboardData toTelemetriaActual(SensorReading reading) {
        return new DashboardPrincipalResponse.TelemetriaActualDashboardData(
                reading.getRecordedAt(),
                reading.getTemperatureC(),
                reading.getHumidityPercent(),
                reading.getNh3Ppm()
        );
    }

    private DashboardPrincipalResponse.TelemetriaMinMaxDiaDashboardData buildMinMax(List<SensorReading> readings) {
        if (readings == null || readings.isEmpty()) {
            return new DashboardPrincipalResponse.TelemetriaMinMaxDiaDashboardData(null, null, null);
        }

        DashboardPrincipalResponse.MinMax temperatura = buildRange(readings, SensorReading::getTemperatureC);
        DashboardPrincipalResponse.MinMax humedad = buildRange(readings, SensorReading::getHumidityPercent);
        DashboardPrincipalResponse.MinMax nh3 = buildRange(readings, SensorReading::getNh3Ppm);

        return new DashboardPrincipalResponse.TelemetriaMinMaxDiaDashboardData(temperatura, humedad, nh3);
    }

    private DashboardPrincipalResponse.MinMax buildRange(List<SensorReading> readings,
                                                         java.util.function.Function<SensorReading, Double> extractor) {
        Optional<Double> min = readings.stream()
                .map(extractor)
                .filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder());

        Optional<Double> max = readings.stream()
                .map(extractor)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder());

        if (min.isEmpty() && max.isEmpty()) {
            return null;
        }

        return new DashboardPrincipalResponse.MinMax(min.orElse(null), max.orElse(null));
    }
}
package com.avimax.sensors.repository;

import com.avimax.sensors.entity.TemperatureReading;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemperatureReadingRepository extends JpaRepository<TemperatureReading, Long> {
    List<TemperatureReading> findByLocationOrderByRecordedAtDesc(String location);
    List<TemperatureReading> findByRecordedAtBetween(OffsetDateTime start, OffsetDateTime end);
}

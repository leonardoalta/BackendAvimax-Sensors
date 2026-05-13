package com.avimax.backend.repository;

import com.avimax.backend.entity.SensorReading;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {
    Optional<SensorReading> findTopByOrderByRecordedAtDesc();

    List<SensorReading> findTop20ByOrderByRecordedAtDesc();

    Optional<SensorReading> findFirstByFlockIdOrderByRecordedAtDesc(Long flockId);

    List<SensorReading> findByFlockIdAndRecordedAtBetweenOrderByRecordedAtDesc(Long flockId,
                                                                              OffsetDateTime start,
                                                                              OffsetDateTime end);
}

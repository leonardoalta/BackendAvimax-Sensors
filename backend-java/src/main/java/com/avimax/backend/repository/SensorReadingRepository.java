package com.avimax.backend.repository;

import com.avimax.backend.entity.SensorReading;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SensorReadingRepository extends JpaRepository<SensorReading, Long>, JpaSpecificationExecutor<SensorReading> {
    Optional<SensorReading> findTopByOrderByRecordedAtDesc();

    List<SensorReading> findTop20ByOrderByRecordedAtDesc();

    Optional<SensorReading> findFirstByFlockIdOrderByRecordedAtDesc(Long flockId);

    List<SensorReading> findByFlockIdAndRecordedAtBetweenOrderByRecordedAtDesc(Long flockId,
                                                                              OffsetDateTime start,
                                                                              OffsetDateTime end);

    Page<SensorReading> findAll(org.springframework.data.jpa.domain.Specification<SensorReading> spec, Pageable pageable);
}

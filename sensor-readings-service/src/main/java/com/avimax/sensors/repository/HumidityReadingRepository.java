package com.avimax.sensors.repository;

import com.avimax.sensors.entity.HumidityReading;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HumidityReadingRepository extends JpaRepository<HumidityReading, Long> {
    List<HumidityReading> findByLocationOrderByRecordedAtDesc(String location);
    List<HumidityReading> findByRecordedAtBetween(OffsetDateTime start, OffsetDateTime end);
}

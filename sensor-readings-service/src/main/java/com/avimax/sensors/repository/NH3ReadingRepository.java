package com.avimax.sensors.repository;

import com.avimax.sensors.entity.NH3Reading;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NH3ReadingRepository extends JpaRepository<NH3Reading, Long> {
    List<NH3Reading> findByLocationOrderByRecordedAtDesc(String location);
    List<NH3Reading> findByRecordedAtBetween(OffsetDateTime start, OffsetDateTime end);
}

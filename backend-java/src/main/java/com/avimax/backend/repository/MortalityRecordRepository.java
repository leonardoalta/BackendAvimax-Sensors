package com.avimax.backend.repository;

import com.avimax.backend.entity.MortalityRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MortalityRecordRepository extends JpaRepository<MortalityRecord, Long> {
    List<MortalityRecord> findAllByOrderByRecordDateDesc();
    List<MortalityRecord> findByRecordDateBetweenOrderByRecordDateDesc(LocalDate from, LocalDate to);
    Optional<MortalityRecord> findByFlockIdAndRecordDate(Long flockId, LocalDate recordDate);
}

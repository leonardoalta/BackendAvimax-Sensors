package com.avimax.backend.repository;

import com.avimax.backend.entity.ConsumptionRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConsumptionRecordRepository extends JpaRepository<ConsumptionRecord, Long> {

    List<ConsumptionRecord> findAllByOrderByRecordDateDesc();

    List<ConsumptionRecord> findByFlockIdOrderByRecordDateDesc(Long flockId);

    Optional<ConsumptionRecord> findFirstByFlockIdOrderByRecordDateDescCreatedAtDesc(Long flockId);
}

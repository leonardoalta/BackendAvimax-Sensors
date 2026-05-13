package com.avimax.backend.repository;

import com.avimax.backend.entity.WeightRecord;
import com.avimax.backend.entity.WeightRecord.Gender;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WeightRecordRepository extends JpaRepository<WeightRecord, Long> {

    /**
     * Find all weight records for a specific flock
     */
    List<WeightRecord> findByFlockIdOrderByRecordDateDesc(Long flockId);

    /**
     * Find all weight records for a specific flock and gender
     */
    List<WeightRecord> findByFlockIdAndGenderOrderByRecordDateDesc(Long flockId, Gender gender);

    /**
     * Find the latest weight record for a specific flock and gender
     */
    Optional<WeightRecord> findFirstByFlockIdAndGenderOrderByRecordDateDescCreatedAtDesc(Long flockId, Gender gender);

    /**
     * Find the latest weight record for a specific flock
     */
    Optional<WeightRecord> findFirstByFlockIdOrderByRecordDateDescCreatedAtDesc(Long flockId);

    /**
     * Find all weight records for a specific location
     */
    List<WeightRecord> findByLocationOrderByRecordDateDesc(String location);

    /**
     * Find weight records by date range for a flock
     */
    @Query("SELECT w FROM WeightRecord w WHERE w.flock.id = :flockId AND w.recordDate BETWEEN :fromDate AND :toDate ORDER BY w.recordDate DESC")
    List<WeightRecord> findByFlockIdAndDateRange(@Param("flockId") Long flockId,
                                                  @Param("fromDate") LocalDate fromDate,
                                                  @Param("toDate") LocalDate toDate);

    /**
     * Find all weight records ordered by date descending
     */
    List<WeightRecord> findAllByOrderByRecordDateDesc();
}

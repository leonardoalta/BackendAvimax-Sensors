package com.avimax.backend.repository;

import com.avimax.backend.entity.ExtractorProgrammingHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractorProgrammingHistoryRepository extends JpaRepository<ExtractorProgrammingHistory, Long> {
    List<ExtractorProgrammingHistory> findByExtractorIdOrderByRecordedAtDesc(Long extractorId);
}

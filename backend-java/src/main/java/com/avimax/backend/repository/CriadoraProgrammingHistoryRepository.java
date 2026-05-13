package com.avimax.backend.repository;

import com.avimax.backend.entity.CriadoraProgrammingHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CriadoraProgrammingHistoryRepository extends JpaRepository<CriadoraProgrammingHistory, Long> {
    List<CriadoraProgrammingHistory> findByCriadoraIdOrderByRecordedAtDesc(Long criadoraId);
}

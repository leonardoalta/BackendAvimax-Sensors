package com.avimax.backend.repository;

import com.avimax.backend.entity.BombaProgrammingHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BombaProgrammingHistoryRepository extends JpaRepository<BombaProgrammingHistory, Long> {
    List<BombaProgrammingHistory> findByBombaIdOrderByRecordedAtDesc(Long bombaId);
}

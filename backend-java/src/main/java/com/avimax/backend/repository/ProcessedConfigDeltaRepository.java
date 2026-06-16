package com.avimax.backend.repository;

import com.avimax.backend.entity.ProcessedConfigDelta;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedConfigDeltaRepository extends JpaRepository<ProcessedConfigDelta, String> {
    // existsById(deltaId) heredado de JpaRepository
}

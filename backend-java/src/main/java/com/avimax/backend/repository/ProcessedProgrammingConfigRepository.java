package com.avimax.backend.repository;

import com.avimax.backend.entity.ProcessedProgrammingConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedProgrammingConfigRepository extends JpaRepository<ProcessedProgrammingConfig, Long> {
}

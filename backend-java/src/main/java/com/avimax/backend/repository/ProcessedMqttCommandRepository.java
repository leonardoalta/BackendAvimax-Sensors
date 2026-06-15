package com.avimax.backend.repository;

import com.avimax.backend.entity.ProcessedMqttCommand;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedMqttCommandRepository extends JpaRepository<ProcessedMqttCommand, Long> {
}

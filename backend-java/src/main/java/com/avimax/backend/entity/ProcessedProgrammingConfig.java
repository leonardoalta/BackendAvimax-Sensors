package com.avimax.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "processed_programming_configs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProcessedProgrammingConfig {

    @Id
    private Long configId;

    private Long galponId;
    private String actuatorType;
    private Long actuatorId;
    private String status;
    private String message;
    private OffsetDateTime firstProcessedAt;
    private OffsetDateTime lastSeenAt;
}

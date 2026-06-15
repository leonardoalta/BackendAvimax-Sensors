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
@Table(name = "processed_mqtt_commands")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProcessedMqttCommand {

    @Id
    private Long commandId;

    private Long galponId;
    private String actuatorType;
    private Long actuatorId;
    private String action;
    private String status;
    private String message;
    private OffsetDateTime firstProcessedAt;
    private OffsetDateTime lastSeenAt;
}

package com.avimax.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

/** Registro de idempotencia: evita aplicar el mismo delta dos veces. */
@Entity
@Table(name = "processed_config_deltas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedConfigDelta {

    @Id
    private String deltaId;

    private String gatewayCode;
    private String actuatorType;
    private String codeName;
    private Long configVersion;
    private OffsetDateTime processedAt;

    @PrePersist
    void prePersist() {
        if (processedAt == null) processedAt = OffsetDateTime.now();
    }
}

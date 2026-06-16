package com.avimax.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "local_config_states")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocalConfigState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long galponId;
    private String gatewayCode;

    @Column(nullable = false)
    private Long configVersion;

    private OffsetDateTime lastDeltaAppliedAt;
    private OffsetDateTime lastPullRequestAt;
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (configVersion == null) configVersion = 0L;
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}

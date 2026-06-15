package com.avimax.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "local_mqtt_outbox_messages")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LocalMqttOutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String topic;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private Integer qos;

    @Column(nullable = false)
    private Boolean retained;

    @Column(nullable = false, length = 50)
    private String messageType;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private Integer attempts;

    private String lastError;

    private OffsetDateTime createdAt;
    private OffsetDateTime lastAttemptAt;
    private OffsetDateTime sentAt;
}

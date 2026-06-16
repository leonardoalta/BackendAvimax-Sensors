package com.avimax.backend.health;

import com.avimax.backend.dto.MqttStatusResponse;
import com.avimax.backend.repository.LocalMqttOutboxMessageRepository;
import com.avimax.backend.service.MqttIngestionService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("localMqtt")
@ConditionalOnBean(MqttIngestionService.class)
public class LocalMqttHealthIndicator implements HealthIndicator {

    private final MqttIngestionService mqttIngestionService;
    private final LocalMqttOutboxMessageRepository outboxRepository;

    public LocalMqttHealthIndicator(MqttIngestionService mqttIngestionService,
                                     LocalMqttOutboxMessageRepository outboxRepository) {
        this.mqttIngestionService = mqttIngestionService;
        this.outboxRepository     = outboxRepository;
    }

    @Override
    public Health health() {
        MqttStatusResponse status = mqttIngestionService.getStatus();
        long outboxPending = outboxRepository.countByStatusIn(List.of("PENDING", "RETRY"));
        long outboxFailed  = outboxRepository.countByStatusIn(List.of("FAILED"));

        Health.Builder builder = status.connected() ? Health.up() : Health.down();

        builder
            .withDetail("brokerUrl",     status.brokerUrl())
            .withDetail("status",        status.connectionStatus())
            .withDetail("outboxPending", outboxPending)
            .withDetail("outboxFailed",  outboxFailed);

        if (outboxFailed > 0) {
            builder.withDetail("warning", "outbox has " + outboxFailed + " FAILED messages");
        }
        if (status.lastError() != null) {
            builder.withDetail("lastError", status.lastError());
        }

        return builder.build();
    }
}

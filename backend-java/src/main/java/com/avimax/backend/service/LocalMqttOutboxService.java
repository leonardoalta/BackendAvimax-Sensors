package com.avimax.backend.service;

import com.avimax.backend.config.MqttProperties;
import com.avimax.backend.entity.LocalMqttOutboxMessage;
import com.avimax.backend.repository.LocalMqttOutboxMessageRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true", matchIfMissing = false)
public class LocalMqttOutboxService {

    private static final Logger log = LoggerFactory.getLogger(LocalMqttOutboxService.class);
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SENT    = "SENT";
    private static final String STATUS_FAILED  = "FAILED";
    private static final String STATUS_DEAD    = "DEAD";
    private static final List<String> RETRYABLE = List.of(STATUS_PENDING, STATUS_FAILED);

    private final LocalMqttOutboxMessageRepository outboxRepository;
    private final MqttProperties mqttProperties;

    @Value("${app.mqtt.outbox.max-attempts:20}")
    private int maxAttempts;

    @Value("${app.mqtt.outbox.batch-size:50}")
    private int batchSize;

    private MqttClient client;

    public LocalMqttOutboxService(LocalMqttOutboxMessageRepository outboxRepository,
                                   MqttProperties mqttProperties) {
        this.outboxRepository = outboxRepository;
        this.mqttProperties = mqttProperties;
    }

    @PostConstruct
    public void start() {
        try {
            client = new MqttClient(mqttProperties.brokerUrl(),
                    mqttProperties.clientId() + "-outbox", new MemoryPersistence());
            client.connect(buildConnectOptions());
            log.info("[Outbox] Conectado a {}", mqttProperties.brokerUrl());
        } catch (Exception e) {
            log.warn("[Outbox] Sin conexión al iniciar (se reintentará automáticamente): {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        try {
            if (client != null && client.isConnected()) client.disconnect();
            if (client != null) client.close();
        } catch (Exception e) {
            log.warn("[Outbox] Error al cerrar: {}", e.getMessage());
        }
    }

    public void enqueue(String topic, String jsonPayload, String messageType) {
        try {
            outboxRepository.save(LocalMqttOutboxMessage.builder()
                    .topic(topic)
                    .payload(jsonPayload)
                    .qos(1)
                    .retained(false)
                    .messageType(messageType)
                    .status(STATUS_PENDING)
                    .attempts(0)
                    .createdAt(OffsetDateTime.now())
                    .build());
            log.info("[Outbox] Encolado — topic={}, type={}", topic, messageType);
        } catch (Exception e) {
            log.error("[Outbox] No se pudo guardar en BD para {}: {}", topic, e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${app.mqtt.outbox.retry-interval-ms:10000}")
    public void retryPending() {
        ensureConnected();
        if (!isConnected()) {
            log.debug("[Outbox] Sin conexión MQTT — omitiendo retry");
            return;
        }
        List<LocalMqttOutboxMessage> pending = outboxRepository
                .findPendingForRetry(RETRYABLE, PageRequest.of(0, batchSize));
        if (pending.isEmpty()) return;

        log.info("[Outbox] Reintentando {} mensajes pendientes", pending.size());
        pending.forEach(this::retryOne);
    }

    private void retryOne(LocalMqttOutboxMessage msg) {
        OffsetDateTime now = OffsetDateTime.now();
        int newAttempts = (msg.getAttempts() != null ? msg.getAttempts() : 0) + 1;
        msg.setAttempts(newAttempts);
        msg.setLastAttemptAt(now);

        try {
            MqttMessage mqttMsg = new MqttMessage(msg.getPayload().getBytes(StandardCharsets.UTF_8));
            mqttMsg.setQos(msg.getQos() != null ? msg.getQos() : 1);
            mqttMsg.setRetained(Boolean.TRUE.equals(msg.getRetained()));
            client.publish(msg.getTopic(), mqttMsg);
            msg.setStatus(STATUS_SENT);
            msg.setSentAt(now);
            log.info("[Outbox] Mensaje {} publicado — topic={}", msg.getId(), msg.getTopic());
        } catch (Exception e) {
            msg.setLastError(e.getMessage());
            if (newAttempts >= maxAttempts) {
                msg.setStatus(STATUS_DEAD);
                log.warn("[Outbox] Mensaje {} marcado DEAD tras {} intentos — {}", msg.getId(), newAttempts, msg.getTopic());
            } else {
                msg.setStatus(STATUS_FAILED);
                log.warn("[Outbox] Intento {}/{} fallido para mensaje {}: {}", newAttempts, maxAttempts, msg.getId(), e.getMessage());
            }
        }
        outboxRepository.save(msg);
    }

    private void ensureConnected() {
        try {
            if (client != null && !client.isConnected()) {
                client.connect(buildConnectOptions());
            }
        } catch (Exception e) {
            log.debug("[Outbox] Reconexión fallida: {}", e.getMessage());
        }
    }

    private boolean isConnected() {
        return client != null && client.isConnected();
    }

    private MqttConnectOptions buildConnectOptions() {
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setAutomaticReconnect(true);
        opts.setCleanSession(true);
        opts.setConnectionTimeout(mqttProperties.connectionTimeoutSeconds());
        opts.setKeepAliveInterval(mqttProperties.keepAliveSeconds());
        if (mqttProperties.username() != null && !mqttProperties.username().isBlank()) {
            opts.setUserName(mqttProperties.username());
        }
        if (mqttProperties.password() != null && !mqttProperties.password().isBlank()) {
            opts.setPassword(mqttProperties.password().toCharArray());
        }
        return opts;
    }
}

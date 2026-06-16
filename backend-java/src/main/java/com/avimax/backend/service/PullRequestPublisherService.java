package com.avimax.backend.service;

import com.avimax.backend.config.MqttProperties;
import com.avimax.backend.repository.LocalConfigStateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fase 3 — Publica un pull-request al central cuando el gateway se reconecta.
 *
 * Publicación: avicola/gateway/{gatewayCode}/sync/pull-request
 *
 * Llamado desde MqttConfigDeltaListenerService.connectComplete(reconnect=true).
 * No se llama en el boot inicial (ese flujo usa BootstrapRequestPublisherService).
 *
 * El central responderá republicando todos los PendingGatewayChange
 * pendientes en orden de configVersion.
 */
@Service
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true", matchIfMissing = false)
public class PullRequestPublisherService {

    private static final Logger log = LoggerFactory.getLogger(PullRequestPublisherService.class);
    private static final int QOS = 1;

    private final MqttProperties mqttProperties;
    private final LocalConfigStateRepository configStateRepository;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private LocalMqttOutboxService outboxService;

    @Value("${app.galpon-id:1}")
    private long configuredGalponId;

    @Value("${app.gateway-id:raspi5-galpon-01}")
    private String configuredGatewayId;

    private MqttClient client;

    public PullRequestPublisherService(MqttProperties mqttProperties,
                                        LocalConfigStateRepository configStateRepository,
                                        ObjectMapper objectMapper) {
        this.mqttProperties       = mqttProperties;
        this.configStateRepository = configStateRepository;
        this.objectMapper         = objectMapper;
    }

    @PostConstruct
    public void init() {
        try {
            client = new MqttClient(mqttProperties.brokerUrl(),
                    mqttProperties.clientId() + "-pull-req-pub", new MemoryPersistence());
            client.connect(buildConnectOptions());
            log.info("[PullReqPub] Conectado a {}", mqttProperties.brokerUrl());
        } catch (Exception e) {
            log.warn("[PullReqPub] Sin conexión MQTT al iniciar: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        try {
            if (client != null && client.isConnected()) client.disconnect();
            if (client != null) client.close();
        } catch (Exception e) {
            log.warn("[PullReqPub] Error al cerrar: {}", e.getMessage());
        }
    }

    /**
     * Publica un pull-request al central con la versión de configuración actual.
     * El central responde enviando los deltas pendientes que el gateway aún no aplicó.
     */
    public void publishPullRequest() {
        long currentVersion = configStateRepository
                .findByGatewayCode(configuredGatewayId)
                .map(s -> s.getConfigVersion())
                .orElse(0L);

        String topic = "avicola/gateway/" + configuredGatewayId + "/sync/pull-request";
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("requestId",           UUID.randomUUID().toString());
            payload.put("gatewayCode",         configuredGatewayId);
            payload.put("galponId",            configuredGalponId);
            payload.put("currentConfigVersion", currentVersion);
            payload.put("reason",              "RECONNECT");
            payload.put("requestedAt",         OffsetDateTime.now().toString());

            String json = objectMapper.writeValueAsString(payload);
            publishOrQueue(topic, json);

            log.info("[PullReqPub] Pull-request publicado — gateway={} currentVersion={}",
                    configuredGatewayId, currentVersion);

        } catch (Exception e) {
            log.error("[PullReqPub] Error publicando pull-request: {}", e.getMessage());
        }
    }

    private void publishOrQueue(String topic, String json) {
        try {
            ensureConnected();
            MqttMessage msg = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
            msg.setQos(QOS);
            msg.setRetained(false);
            client.publish(topic, msg);
        } catch (Exception e) {
            log.warn("[PullReqPub] MQTT no disponible, encolando pull-request: {}", e.getMessage());
            if (outboxService != null) {
                outboxService.enqueue(topic, json, "PULL_REQUEST");
            } else {
                log.error("[PullReqPub] Outbox no disponible — pull-request perdido");
            }
        }
    }

    private void ensureConnected() throws Exception {
        if (client != null && !client.isConnected()) {
            client.connect(buildConnectOptions());
        }
    }

    private MqttConnectOptions buildConnectOptions() {
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setAutomaticReconnect(true);
        opts.setCleanSession(true);
        opts.setConnectionTimeout(mqttProperties.connectionTimeoutSeconds());
        opts.setKeepAliveInterval(mqttProperties.keepAliveSeconds());
        return opts;
    }
}

package com.avimax.backend.service;

import com.avimax.backend.config.MqttProperties;
import com.avimax.backend.dto.BootstrapSnapshotDto;
import com.avimax.backend.service.BootstrapSnapshotApplyService.ApplyResult;
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

/**
 * Fase 2 — Recibe el snapshot de bootstrap desde el central.
 *
 * Suscripción: avicola/gateway/{gatewayCode}/bootstrap/snapshot
 * Publicación: avicola/gateway/{gatewayCode}/bootstrap/ack
 */
@Service
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true", matchIfMissing = false)
public class MqttBootstrapSnapshotListenerService {

    private static final Logger log = LoggerFactory.getLogger(MqttBootstrapSnapshotListenerService.class);
    private static final int QOS = 1;

    private final MqttProperties mqttProperties;
    private final BootstrapSnapshotApplyService applyService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private LocalMqttOutboxService outboxService;

    @Value("${app.galpon-id:1}")
    private long configuredGalponId;

    @Value("${app.gateway-id:raspi5-galpon-01}")
    private String configuredGatewayId;

    private MqttClient client;

    public MqttBootstrapSnapshotListenerService(MqttProperties mqttProperties,
                                                 BootstrapSnapshotApplyService applyService,
                                                 ObjectMapper objectMapper) {
        this.mqttProperties = mqttProperties;
        this.applyService   = applyService;
        this.objectMapper   = objectMapper;
    }

    @PostConstruct
    public void start() {
        try {
            client = new MqttClient(mqttProperties.brokerUrl(),
                    mqttProperties.clientId() + "-bootstrap-snap", new MemoryPersistence());
            client.connect(buildConnectOptions());

            // Suscripción específica a nuestro propio gatewayCode
            String topic = "avicola/gateway/" + configuredGatewayId + "/bootstrap/snapshot";
            client.subscribe(topic, QOS, this::handleSnapshot);
            log.info("[BootstrapSnap] Suscrito a {} en {}", topic, mqttProperties.brokerUrl());
        } catch (Exception e) {
            log.error("[BootstrapSnap] No se pudo iniciar: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        try {
            if (client != null && client.isConnected()) client.disconnect();
            if (client != null) client.close();
        } catch (Exception e) {
            log.warn("[BootstrapSnap] Error al cerrar: {}", e.getMessage());
        }
    }

    private void handleSnapshot(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        log.info("[BootstrapSnap] Snapshot recibido ({}B)", payload.length());

        String requestId  = null;
        String snapshotId = null;
        ApplyResult result;

        try {
            BootstrapSnapshotDto snapshot = objectMapper.readValue(payload, BootstrapSnapshotDto.class);
            requestId  = snapshot.getRequestId();
            snapshotId = snapshot.getSnapshotId();

            log.info("[BootstrapSnap] Aplicando snapshot snapshotId={} actuadores={}",
                    snapshotId, snapshot.getActuators() != null ? snapshot.getActuators().size() : 0);

            result = applyService.apply(snapshot);

        } catch (Exception e) {
            log.error("[BootstrapSnap] Error procesando snapshot: {}", e.getMessage(), e);
            result = new ApplyResult(0, 0, 0, 0, "Error procesando snapshot: " + e.getMessage());
        }

        publishAck(requestId, snapshotId, result);
    }

    private void publishAck(String requestId, String snapshotId, ApplyResult result) {
        String ackTopic = "avicola/gateway/" + configuredGatewayId + "/bootstrap/ack";
        try {
            Map<String, Object> ack = new LinkedHashMap<>();
            ack.put("requestId",         requestId);
            ack.put("snapshotId",        snapshotId);
            ack.put("galponId",          configuredGalponId);
            ack.put("gatewayCode",       configuredGatewayId);
            ack.put("status",            result.isSuccess() ? "APPLIED" : "ERROR");
            ack.put("message",           result.isSuccess()
                    ? "Bootstrap aplicado correctamente"
                    : "Error: " + result.errorMessage());
            ack.put("receivedActuators",  result.received());
            ack.put("createdActuators",   result.created());
            ack.put("updatedActuators",   result.updated());
            ack.put("disabledActuators",  result.disabled());
            ack.put("appliedAt",         OffsetDateTime.now().toString());

            String ackJson = objectMapper.writeValueAsString(ack);
            publishOrQueue(ackTopic, ackJson);

            log.info("[BootstrapSnap] ACK publicado — status={} creados={} actualizados={} deshabilitados={}",
                    ack.get("status"), result.created(), result.updated(), result.disabled());

        } catch (Exception e) {
            log.error("[BootstrapSnap] No se pudo publicar ACK: {}", e.getMessage());
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
            log.warn("[BootstrapSnap] MQTT no disponible, encolando ACK: {}", e.getMessage());
            if (outboxService != null) {
                outboxService.enqueue(topic, json, "BOOTSTRAP_ACK");
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

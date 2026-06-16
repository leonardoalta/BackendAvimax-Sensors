package com.avimax.backend.service;

import com.avimax.backend.config.MqttProperties;
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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fase 2 — Publica la solicitud de bootstrap al central cuando la aplicación está lista.
 *
 * Publicación: avicola/gateway/{gatewayCode}/bootstrap/request
 *
 * Se dispara después de ApplicationReadyEvent (todos los @PostConstruct terminaron),
 * por lo que el listener de snapshot ya está suscrito cuando llega la respuesta.
 */
@Service
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true", matchIfMissing = false)
public class BootstrapRequestPublisherService {

    private static final Logger log = LoggerFactory.getLogger(BootstrapRequestPublisherService.class);
    private static final int QOS = 1;

    private final MqttProperties mqttProperties;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private LocalMqttOutboxService outboxService;

    @Value("${app.galpon-id:1}")
    private long configuredGalponId;

    @Value("${app.gateway-id:raspi5-galpon-01}")
    private String configuredGatewayId;

    @Value("${app.bootstrap.enabled:true}")
    private boolean bootstrapEnabled;

    private MqttClient client;

    public BootstrapRequestPublisherService(MqttProperties mqttProperties, ObjectMapper objectMapper) {
        this.mqttProperties = mqttProperties;
        this.objectMapper   = objectMapper;
    }

    @PostConstruct
    public void init() {
        try {
            client = new MqttClient(mqttProperties.brokerUrl(),
                    mqttProperties.clientId() + "-bootstrap-pub", new MemoryPersistence());
            client.connect(buildConnectOptions());
            log.info("[BootstrapPub] Conectado a {}", mqttProperties.brokerUrl());
        } catch (Exception e) {
            log.warn("[BootstrapPub] Sin conexión MQTT al iniciar: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        try {
            if (client != null && client.isConnected()) client.disconnect();
            if (client != null) client.close();
        } catch (Exception e) {
            log.warn("[BootstrapPub] Error al cerrar: {}", e.getMessage());
        }
    }

    /**
     * Se ejecuta una vez que todo Spring Boot está listo.
     * Así el MqttBootstrapSnapshotListenerService ya está suscrito al topic de snapshot.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void publishBootstrapRequest() {
        if (!bootstrapEnabled) {
            log.info("[BootstrapPub] Bootstrap deshabilitado (app.bootstrap.enabled=false)");
            return;
        }

        String requestTopic = "avicola/gateway/" + configuredGatewayId + "/bootstrap/request";
        String requestId    = UUID.randomUUID().toString();

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("requestId",            requestId);
            payload.put("galponId",             configuredGalponId);
            payload.put("gatewayCode",          configuredGatewayId);
            payload.put("localStartedAt",       OffsetDateTime.now().toString());
            payload.put("currentConfigVersion", null);
            payload.put("reason",               "STARTUP");

            String json = objectMapper.writeValueAsString(payload);
            publishOrQueue(requestTopic, json);

            log.info("[BootstrapPub] Bootstrap request publicado — requestId={} gateway={} galpon={}",
                    requestId, configuredGatewayId, configuredGalponId);

        } catch (Exception e) {
            log.error("[BootstrapPub] Error publicando bootstrap request: {}", e.getMessage(), e);
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
            log.warn("[BootstrapPub] Publicación directa fallida, encolando en outbox: {}", e.getMessage());
            if (outboxService != null) {
                outboxService.enqueue(topic, json, "BOOTSTRAP_REQUEST");
            } else {
                log.error("[BootstrapPub] Outbox no disponible — bootstrap request perdido");
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

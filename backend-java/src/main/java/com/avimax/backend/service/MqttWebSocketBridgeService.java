package com.avimax.backend.service;

import com.avimax.backend.config.MqttProperties;
import com.avimax.backend.websocket.WebSocketSessions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true", matchIfMissing = false)
public class MqttWebSocketBridgeService {

    private static final Logger log = LoggerFactory.getLogger(MqttWebSocketBridgeService.class);

    private final MqttProperties mqttProperties;
    private final WebSocketSessions webSocketSessions;
    private final ObjectMapper objectMapper;

    private MqttClient client;

    public static final class MqttPublishRequest {
        private final Object source;
        private final String rawJson;

        public MqttPublishRequest(Object source, String rawJson) {
            this.source = source;
            this.rawJson = rawJson;
        }

        public String getRawJson() {
            return rawJson;
        }
    }

    public MqttWebSocketBridgeService(MqttProperties mqttProperties, WebSocketSessions webSocketSessions, ObjectMapper objectMapper) {
        this.mqttProperties = mqttProperties;
        this.webSocketSessions = webSocketSessions;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void start() {
        try {
            client = new MqttClient(mqttProperties.brokerUrl(), mqttProperties.clientId() + "-wsbridge", new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(mqttProperties.connectionTimeoutSeconds());
            options.setKeepAliveInterval(mqttProperties.keepAliveSeconds());

            if (mqttProperties.username() != null && !mqttProperties.username().isBlank()) {
                options.setUserName(mqttProperties.username());
            }
            if (mqttProperties.password() != null && !mqttProperties.password().isBlank()) {
                options.setPassword(mqttProperties.password().toCharArray());
            }

            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("MQTT bridge conexión perdida", cause);
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String body = new String(message.getPayload(), StandardCharsets.UTF_8);
                    // Forward the envelope or raw message to WS clients
                    // If payload is an envelope (topic,payload,meta) forward as-is
                    try {
                        JsonNode node = objectMapper.readTree(body);
                        webSocketSessions.broadcast(objectMapper.writeValueAsString(node));
                    } catch (Exception e) {
                        // not JSON — wrap
                        webSocketSessions.broadcast(objectMapper.writeValueAsString(objectMapper.createObjectNode().put("topic", topic).put("payload", body)));
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });

            client.connect(options);

            // subscribe to actuator topics, aggregated state and alarm alerts
            client.subscribe("avimax/actuator/+/+/state", mqttProperties.qos());
            client.subscribe("avimax/actuators/state", mqttProperties.qos());
            client.subscribe("avimax/galpon1/alertas", mqttProperties.qos());

            log.info("MQTT↔WS bridge conectado a {} — suscrito a topics de actuadores y alertas", mqttProperties.brokerUrl());
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible iniciar MQTT↔WS bridge", e);
        }
    }

    @PreDestroy
    public void stop() {
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
            }
            if (client != null) {
                client.close();
            }
        } catch (Exception e) {
            log.warn("Error cerrando MQTT↔WS bridge", e);
        }
    }

    @EventListener
    public void onPublishRequest(MqttPublishRequest ev) {
        String raw = ev.getRawJson();
        try {
            // Expecting JSON with { topic, payload, retained? }
            JsonNode node = objectMapper.readTree(raw);
            String topic = node.has("topic") ? node.get("topic").asText() : null;
            JsonNode payload = node.has("payload") ? node.get("payload") : null;
            boolean retained = node.has("retained") && node.get("retained").asBoolean(false);

            if (topic == null || topic.isBlank()) {
                log.warn("Publish request without topic: {}", raw);
                return;
            }

            byte[] body = payload == null ? new byte[0] : objectMapper.writeValueAsBytes(payload);
            MqttMessage msg = new MqttMessage(body);
            msg.setQos(mqttProperties.qos());
            msg.setRetained(retained);
            client.publish(topic, msg);
            log.info("Publicado desde WS a MQTT {} retained={} size={}", topic, retained, body.length);
        } catch (Exception e) {
            log.warn("Error publicando via bridge WS->MQTT: {}", raw, e);
        }
    }
}

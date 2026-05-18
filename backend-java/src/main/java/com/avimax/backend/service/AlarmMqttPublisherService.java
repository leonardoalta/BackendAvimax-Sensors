package com.avimax.backend.service;

import com.avimax.backend.config.MqttProperties;
import com.avimax.backend.entity.Alarm;
import com.avimax.backend.entity.AlarmEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true", matchIfMissing = false)
public class AlarmMqttPublisherService {

    private static final Logger log = LoggerFactory.getLogger(AlarmMqttPublisherService.class);

    public static final String ALARMS_TOPIC = "avimax/galpon1/alertas";

    private final ObjectMapper objectMapper;
    private final MqttProperties mqttProperties;

    private MqttClient client;

    public AlarmMqttPublisherService(ObjectMapper objectMapper, MqttProperties mqttProperties) {
        this.objectMapper = objectMapper;
        this.mqttProperties = mqttProperties;
    }

    @PostConstruct
    public void start() {
        try {
            client = new MqttClient(mqttProperties.brokerUrl(), mqttProperties.clientId() + "-alarm-publisher", new MemoryPersistence());

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

            client.connect(options);
            log.info("MQTT alarm publisher conectado a {}", mqttProperties.brokerUrl());
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible iniciar el publicador MQTT de alarmas", e);
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
            log.warn("Error cerrando publicador MQTT de alarmas", e);
        }
    }

    public void publishEvent(AlarmEventType eventType, Alarm alarm, String messageOverride, OffsetDateTime eventAt) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("tipoEvento", eventType.name());
            payload.put("idAlarma", alarm.getId());
            payload.put("idRegla", alarm.getRule().getId());
            payload.put("nombre", alarm.getRuleName());
            payload.put("variable", alarm.getVariable().name().toLowerCase());
            payload.put("valorDetectado", alarm.getDetectedValue());
            payload.put("umbral", alarm.getThreshold());
            payload.put("unidad", alarm.getUnit());
            payload.put("condicion", alarm.getConditionType().name().toLowerCase());
            payload.put("severidad", alarm.getSeverity().name().toLowerCase());
            payload.put("mensaje", messageOverride != null ? messageOverride : alarm.getMessage());
            payload.put("estado", alarm.getStatus().name().toLowerCase());
            payload.put("fechaHora", eventAt != null ? eventAt.toString() : OffsetDateTime.now(ZoneOffset.UTC).toString());

            byte[] body = objectMapper.writeValueAsBytes(payload);
            MqttMessage mqttMessage = new MqttMessage(body);
            mqttMessage.setQos(mqttProperties.qos());
            mqttMessage.setRetained(false);
            client.publish(ALARMS_TOPIC, mqttMessage);
        } catch (Exception e) {
            log.error("Error publicando evento MQTT de alarma {} para alarma {}", eventType, alarm.getId(), e);
        }
    }
}

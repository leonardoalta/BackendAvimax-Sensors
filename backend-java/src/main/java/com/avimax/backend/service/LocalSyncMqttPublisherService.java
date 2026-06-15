package com.avimax.backend.service;

import com.avimax.backend.config.MqttProperties;
import com.avimax.backend.entity.ActuatorControlState;
import com.avimax.backend.entity.ConsumptionRecord;
import com.avimax.backend.entity.MortalityRecord;
import com.avimax.backend.entity.WeightRecord;
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
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class LocalSyncMqttPublisherService {

    private static final Logger log = LoggerFactory.getLogger(LocalSyncMqttPublisherService.class);
    private static final int QOS = 1;
    private static final boolean RETAINED = false;
    private static final String ORIGIN = "LOCAL";
    private static final String FIELD_RECORD_DATE = "recordDate";
    private static final String FIELD_CREATED_AT  = "createdAt";
    private static final String FIELD_SYNCED_AT   = "syncedAt";

    private final MqttProperties mqttProperties;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private LocalMqttOutboxService outboxService;

    @Value("${app.galpon-id:1}")
    private long configuredGalponId;

    @Value("${app.gateway-id:raspi5-galpon-01}")
    private String configuredGatewayId;

    private MqttClient client;

    public LocalSyncMqttPublisherService(MqttProperties mqttProperties, ObjectMapper objectMapper) {
        this.mqttProperties = mqttProperties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void start() {
        if (!mqttProperties.enabled()) return;
        try {
            client = new MqttClient(mqttProperties.brokerUrl(),
                    mqttProperties.clientId() + "-sync-pub", new MemoryPersistence());
            client.connect(buildConnectOptions());
            log.info("[SyncPub] Conectado a {}", mqttProperties.brokerUrl());
        } catch (Exception e) {
            log.error("[SyncPub] No se pudo iniciar: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        try {
            if (client != null && client.isConnected()) client.disconnect();
            if (client != null) client.close();
        } catch (Exception e) {
            log.warn("[SyncPub] Error al cerrar: {}", e.getMessage());
        }
    }

    public void publishMortalityRecorded(MortalityRecord mortality, Long flockId) {
        Map<String, Object> payload = basePayload("MORTALITY_RECORDED", mortality.getId(), flockId);
        payload.put(FIELD_RECORD_DATE, str(mortality.getRecordDate()));
        payload.put("ageDays", mortality.getAgeDays());
        payload.put("maleCount", mortality.getMaleCount());
        payload.put("femaleCount", mortality.getFemaleCount());
        payload.put("totalCount", mortality.getTotalCount());
        payload.put("observations", mortality.getObservations());
        payload.put(FIELD_CREATED_AT, str(mortality.getCreatedAt()));
        payload.put(FIELD_SYNCED_AT, OffsetDateTime.now().toString());
        publishOrQueue(syncTopic("mortality"), payload, "SYNC_MORTALITY");
    }

    public void publishWeightRecorded(WeightRecord weight, Long flockId) {
        Map<String, Object> payload = basePayload("WEIGHT_RECORDED", weight.getId(), flockId);
        payload.put("sampledBirdsCount", weight.getSampledBirdsCount());
        payload.put("averageWeight", weight.getAverageWeight());
        payload.put("age", weight.getAge());
        payload.put(FIELD_RECORD_DATE, str(weight.getRecordDate()));
        payload.put("gender", weight.getGender() != null ? weight.getGender().name() : null);
        payload.put("location", weight.getLocation() != null ? weight.getLocation().name() : null);
        payload.put(FIELD_CREATED_AT, str(weight.getCreatedAt()));
        payload.put(FIELD_SYNCED_AT, OffsetDateTime.now().toString());
        publishOrQueue(syncTopic("weight"), payload, "SYNC_WEIGHT");
    }

    public void publishConsumptionRecorded(ConsumptionRecord consumption, Long flockId) {
        Map<String, Object> payload = basePayload("CONSUMPTION_RECORDED", consumption.getId(), flockId);
        payload.put("age", consumption.getAge());
        payload.put(FIELD_RECORD_DATE, str(consumption.getRecordDate()));
        payload.put("totalConsumptionKg", consumption.getTotalConsumptionKg());
        payload.put("birdsCountUsed", consumption.getBirdsCountUsed());
        payload.put("consumptionPerBirdKg", consumption.getConsumptionPerBirdKg());
        payload.put(FIELD_CREATED_AT, str(consumption.getCreatedAt()));
        payload.put(FIELD_SYNCED_AT, OffsetDateTime.now().toString());
        publishOrQueue(syncTopic("consumption"), payload, "SYNC_CONSUMPTION");
    }

    public void publishActuatorStateChanged(ActuatorControlState state, String triggeredBy,
                                             Integer workDurationSeconds) {
        Map<String, Object> payload = basePayload("ACTUATOR_STATE_CHANGED", null, null);
        payload.put("actuatorType", state.getActuatorType());
        payload.put("actuatorId", state.getActuatorId());
        payload.put("actuatorName", state.getActuatorName());
        payload.put("state", state.isCurrentState());
        payload.put("command", state.isCurrentState() ? "ON" : "OFF");
        payload.put("triggeredBy", triggeredBy);
        payload.put("workDurationSeconds", workDurationSeconds);
        payload.put("changedAt", str(state.getLastUpdatedAt()));
        payload.put(FIELD_SYNCED_AT, OffsetDateTime.now().toString());
        publishOrQueue(syncTopic("actuator-state"), payload, "SYNC_ACTUATOR_STATE");
    }

    private void publishOrQueue(String topic, Map<String, Object> payload, String messageType) {
        if (!mqttProperties.enabled()) return;

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("[SyncPub] Error serializando payload para {}: {}", topic, e.getMessage());
            return;
        }

        if (client == null || !client.isConnected()) {
            log.warn("[SyncPub] Sin conexión MQTT, encolando en outbox: {}", topic);
            enqueueIfPossible(topic, json, messageType);
            return;
        }
        try {
            MqttMessage msg = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
            msg.setQos(QOS);
            msg.setRetained(RETAINED);
            client.publish(topic, msg);
            log.debug("[SyncPub] Publicado en {}", topic);
        } catch (Exception e) {
            log.warn("[SyncPub] Falló publicación en {}, encolando: {}", topic, e.getMessage());
            enqueueIfPossible(topic, json, messageType);
        }
    }

    private void enqueueIfPossible(String topic, String json, String messageType) {
        if (outboxService != null) {
            outboxService.enqueue(topic, json, messageType);
        } else {
            log.warn("[SyncPub] Outbox no disponible, mensaje perdido para {}", topic);
        }
    }

    private Map<String, Object> basePayload(String eventType, Long localEntityId, Long flockId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eventId", UUID.randomUUID().toString());
        m.put("eventType", eventType);
        m.put("origin", ORIGIN);
        m.put("galponId", configuredGalponId);
        m.put("gatewayId", configuredGatewayId);
        m.put("localEntityId", localEntityId);
        m.put("flockId", flockId);
        return m;
    }

    private String syncTopic(String eventType) {
        return "avicola/galpon/" + configuredGalponId + "/sync/" + eventType;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private MqttConnectOptions buildConnectOptions() {
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
        return options;
    }
}

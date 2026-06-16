package com.avimax.backend.service;

import com.avimax.backend.config.MqttProperties;
import com.avimax.backend.entity.Alarm;
import com.avimax.backend.entity.AlarmRule;
import com.avimax.backend.entity.ActuatorControlState;
import com.avimax.backend.entity.Bomba;
import com.avimax.backend.entity.ConsumptionRecord;
import com.avimax.backend.entity.Criadora;
import com.avimax.backend.entity.Extractor;
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
    private static final String FIELD_RECORD_DATE   = "recordDate";
    private static final String FIELD_CREATED_AT    = "createdAt";
    private static final String FIELD_SYNCED_AT     = "syncedAt";
    private static final String FIELD_ACTUATOR_TYPE = "actuatorType";
    private static final String FIELD_CODE_NAME     = "codeName";

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

    // ── Actuadores (CRUD local → central) ────────────────────────────────────

    public void publishActuatorUpsert(Extractor extractor, String eventType) {
        publishActuatorUpsertRaw("EXTRACTOR", extractor.getId(), extractor.getCentralActuatorId(),
                extractor.getCodeName(), extractor.getName(), extractor.isEnabled(), eventType);
    }

    public void publishActuatorUpsert(Criadora criadora, String eventType) {
        publishActuatorUpsertRaw("CRIADORA", criadora.getId(), criadora.getCentralActuatorId(),
                criadora.getCodeName(), criadora.getName(), criadora.isEnabled(), eventType);
    }

    public void publishActuatorUpsert(Bomba bomba, String eventType) {
        publishActuatorUpsertRaw("BOMBA", bomba.getId(), bomba.getCentralActuatorId(),
                bomba.getCodeName(), bomba.getName(), bomba.isEnabled(), eventType);
    }

    private void publishActuatorUpsertRaw(String actuatorType, Long localId, Long centralId,
                                          String codeName, String name, boolean enabled,
                                          String eventType) {
        Map<String, Object> payload = basePayload(eventType, localId, null);
        payload.put(FIELD_ACTUATOR_TYPE, actuatorType);
        payload.put("localActuatorId", localId);
        payload.put("centralActuatorId", centralId);
        payload.put(FIELD_CODE_NAME, codeName);
        payload.put("name", name);
        payload.put("enabled", enabled);
        payload.put("changedAt", OffsetDateTime.now().toString());
        publishOrQueue(syncTopic("actuator-upsert"), payload, "SYNC_ACTUATOR_UPSERT");
    }

    /** Publica cambio de estado con codeName para que central identifique el actuador correctamente. */
    public void publishActuatorStateChanged(ActuatorControlState state, String codeName,
                                             String triggeredBy, Integer workDurationSeconds) {
        Map<String, Object> payload = basePayload("ACTUATOR_STATE_CHANGED", null, null);
        payload.put(FIELD_ACTUATOR_TYPE, state.getActuatorType());
        payload.put("localActuatorId", state.getActuatorId());
        payload.put("actuatorId", state.getActuatorId()); // backward compat
        payload.put(FIELD_CODE_NAME, codeName);
        payload.put("actuatorName", state.getActuatorName());
        payload.put("state", state.isCurrentState());
        payload.put("command", state.isCurrentState() ? "ON" : "OFF");
        payload.put("triggeredBy", triggeredBy);
        payload.put("workDurationSeconds", workDurationSeconds);
        payload.put("changedAt", str(state.getLastUpdatedAt()));
        payload.put(FIELD_SYNCED_AT, OffsetDateTime.now().toString());
        publishOrQueue(syncTopic("actuator-state"), payload, "SYNC_ACTUATOR_STATE");
    }

    /** Overload sin codeName para callers internos que no disponen de él (legacy). */
    public void publishActuatorStateChanged(ActuatorControlState state, String triggeredBy,
                                             Integer workDurationSeconds) {
        publishActuatorStateChanged(state, null, triggeredBy, workDurationSeconds);
    }

    // ── Alarmas ──────────────────────────────────────────────────────────────

    public void publishAlarmTriggered(Alarm alarm) {
        publishAlarmEvent("ALARM_TRIGGERED", alarm, alarm.getActivatedAt());
    }

    public void publishAlarmAcknowledged(Alarm alarm) {
        publishAlarmEvent("ALARM_ACKNOWLEDGED", alarm, alarm.getAcknowledgedAt());
    }

    public void publishAlarmResolved(Alarm alarm) {
        publishAlarmEvent("ALARM_RESOLVED", alarm, alarm.getResolvedAt());
    }

    public void publishAlarmClosed(Alarm alarm) {
        publishAlarmEvent("ALARM_CLOSED", alarm, alarm.getClosedAt());
    }

    private void publishAlarmEvent(String eventType, Alarm alarm, OffsetDateTime eventAt) {
        Map<String, Object> payload = basePayload(eventType, alarm.getId(), null);
        payload.put("alarmRuleId", alarm.getRule() != null ? alarm.getRule().getId() : null);
        payload.put("alarmRuleName", alarm.getRuleName());
        payload.put("variable", alarm.getVariable() != null ? alarm.getVariable().name() : null);
        payload.put("detectedValue", alarm.getDetectedValue());
        payload.put("threshold", alarm.getThreshold());
        payload.put("severity", alarm.getSeverity() != null ? alarm.getSeverity().name() : null);
        payload.put("status", alarm.getStatus() != null ? alarm.getStatus().name() : null);
        payload.put("message", alarm.getMessage());
        payload.put("eventAt", eventAt != null ? eventAt.toString() : OffsetDateTime.now().toString());
        payload.put(FIELD_SYNCED_AT, OffsetDateTime.now().toString());
        publishOrQueue(syncTopic("alarm-event"), payload, "SYNC_ALARM_EVENT");
    }

    // ── Reglas de alarma ─────────────────────────────────────────────────────

    public void publishAlarmRuleChanged(AlarmRule rule, String changeType) {
        Map<String, Object> payload = basePayload("ALARM_RULE_CHANGED", rule.getId(), null);
        payload.put("changeType", changeType);
        payload.put("ruleName", rule.getName());
        payload.put("variable", rule.getVariable() != null ? rule.getVariable().name() : null);
        payload.put("conditionType", rule.getConditionType() != null ? rule.getConditionType().name() : null);
        payload.put("threshold", rule.getThreshold());
        payload.put("unit", rule.getUnit());
        payload.put("minimumDurationSeconds", rule.getMinimumDurationSeconds());
        payload.put("severity", rule.getSeverity() != null ? rule.getSeverity().name() : null);
        payload.put("message", rule.getMessage());
        payload.put("active", rule.isActive());
        payload.put(FIELD_SYNCED_AT, OffsetDateTime.now().toString());
        publishOrQueue(syncTopic("alarm-rule-update"), payload, "SYNC_ALARM_RULE");
    }

    // ── Programación ─────────────────────────────────────────────────────────

    public void publishProgrammingChanged(String actuatorType, String codeName,
                                          Double temperatureOn, Double temperatureOff,
                                          Integer workDurationSeconds) {
        Map<String, Object> payload = basePayload("PROGRAMMING_CHANGED", null, null);
        payload.put(FIELD_ACTUATOR_TYPE, actuatorType);
        payload.put(FIELD_CODE_NAME, codeName);
        payload.put("temperatureOn", temperatureOn);
        payload.put("temperatureOff", temperatureOff);
        payload.put("workDurationSeconds", workDurationSeconds);
        payload.put(FIELD_SYNCED_AT, OffsetDateTime.now().toString());
        publishOrQueue(syncTopic("programming-update"), payload, "SYNC_PROGRAMMING");
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

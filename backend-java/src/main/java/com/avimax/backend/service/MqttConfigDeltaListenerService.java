package com.avimax.backend.service;

import com.avimax.backend.config.MqttProperties;
import com.avimax.backend.entity.*;
import com.avimax.backend.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fase 3 — Recibe deltas de configuración del central y los aplica localmente.
 *
 * Suscripción: avicola/gateway/{gatewayCode}/config/delta
 * Publicación: avicola/gateway/{gatewayCode}/config/delta/ack
 *
 * En cada reconexión MQTT (no boot inicial) se envía un pull-request al central
 * para solicitar los cambios pendientes desde la última versión aplicada.
 */
@Service
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true", matchIfMissing = false)
public class MqttConfigDeltaListenerService implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttConfigDeltaListenerService.class);
    private static final int    QOS            = 1;
    private static final String GW_TOPIC_BASE  = "avicola/gateway/";
    private static final String CONFIG_UPDATE  = "CONFIG_UPDATE";
    private static final String PROG_UPDATE    = "PROGRAMMING_UPDATE";
    private static final String FIELD_TEMP_ON  = "temperatureOn";
    private static final String FIELD_TEMP_OFF = "temperatureOff";
    private static final String FIELD_ENABLED  = "enabled";
    private static final String FIELD_CFG_VER  = "configVersion";
    private static final String FIELD_MESSAGE      = "message";
    private static final String FIELD_VARIABLE     = "variable";
    private static final String FIELD_CONDITION    = "conditionType";
    private static final String FIELD_SEVERITY     = "severity";
    private static final String FIELD_THRESHOLD    = "threshold";
    private static final String FIELD_UNIT         = "unit";
    private static final String FIELD_MIN_DURATION = "minimumDurationSeconds";
    private static final String FIELD_ACTIVE       = "active";

    private static final String ALARM_RULE_CREATED  = "ALARM_RULE_CREATED";
    private static final String ALARM_RULE_UPDATED  = "ALARM_RULE_UPDATED";
    private static final String ALARM_RULE_ENABLED  = "ALARM_RULE_ENABLED";
    private static final String ALARM_RULE_DISABLED  = "ALARM_RULE_DISABLED";
    private static final String PRODUCTIVE_SVC_MISSING = "LocalProductiveRecordDeltaService no disponible";

    private final MqttProperties mqttProperties;
    private final ObjectMapper objectMapper;
    private final ProcessedConfigDeltaRepository processedDeltaRepository;
    private final LocalConfigStateRepository configStateRepository;
    private final ExtractorRepository extractorRepository;
    private final ExtractorProgrammingRepository extractorProgrammingRepository;
    private final CriadoraRepository criadoraRepository;
    private final CriadoraProgrammingRepository criadoraProgrammingRepository;
    private final BombaRepository bombaRepository;
    private final BombaProgrammingRepository bombaProgrammingRepository;
    private final AlarmRuleRepository alarmRuleRepository;

    @Autowired(required = false)
    private LocalMqttOutboxService outboxService;

    @Autowired(required = false)
    private PullRequestPublisherService pullRequestPublisher;

    @Autowired(required = false)
    private LocalProductiveRecordDeltaService productiveRecordDeltaService;

    @Value("${app.galpon-id:1}")
    private long configuredGalponId;

    @Value("${app.gateway-id:raspi5-galpon-01}")
    private String configuredGatewayId;

    private MqttClient client;

    public MqttConfigDeltaListenerService(MqttProperties mqttProperties,
                                           ObjectMapper objectMapper,
                                           ProcessedConfigDeltaRepository processedDeltaRepository,
                                           LocalConfigStateRepository configStateRepository,
                                           ExtractorRepository extractorRepository,
                                           ExtractorProgrammingRepository extractorProgrammingRepository,
                                           CriadoraRepository criadoraRepository,
                                           CriadoraProgrammingRepository criadoraProgrammingRepository,
                                           BombaRepository bombaRepository,
                                           BombaProgrammingRepository bombaProgrammingRepository,
                                           AlarmRuleRepository alarmRuleRepository) {
        this.mqttProperties                 = mqttProperties;
        this.objectMapper                   = objectMapper;
        this.processedDeltaRepository       = processedDeltaRepository;
        this.configStateRepository          = configStateRepository;
        this.extractorRepository            = extractorRepository;
        this.extractorProgrammingRepository = extractorProgrammingRepository;
        this.criadoraRepository             = criadoraRepository;
        this.criadoraProgrammingRepository  = criadoraProgrammingRepository;
        this.bombaRepository                = bombaRepository;
        this.bombaProgrammingRepository     = bombaProgrammingRepository;
        this.alarmRuleRepository            = alarmRuleRepository;
    }

    @PostConstruct
    public void start() {
        try {
            client = new MqttClient(mqttProperties.brokerUrl(),
                    mqttProperties.clientId() + "-cfg-delta", new MemoryPersistence());
            client.setCallback(this);
            client.connect(buildConnectOptions());
            client.subscribe(deltaTopic(), QOS);
            log.info("[DeltaListener] Suscrito a {} en {}", deltaTopic(), mqttProperties.brokerUrl());
        } catch (Exception e) {
            log.error("[DeltaListener] No se pudo iniciar: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        try {
            if (client != null && client.isConnected()) client.disconnect();
            if (client != null) client.close();
        } catch (Exception e) {
            log.warn("[DeltaListener] Error al cerrar: {}", e.getMessage());
        }
    }

    // ── MqttCallbackExtended ──────────────────────────────────────────────────

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        if (!reconnect) return;
        log.info("[DeltaListener] Reconectado a MQTT — re-suscribiendo y enviando pull-request");
        try {
            client.subscribe(deltaTopic(), QOS);
        } catch (Exception e) {
            log.warn("[DeltaListener] Error re-suscribiendo tras reconexión: {}", e.getMessage());
        }
        if (pullRequestPublisher != null) {
            pullRequestPublisher.publishPullRequest();
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("[DeltaListener] Conexión perdida: {}", cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        handleDelta(topic, message);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) { /* no-op */ }

    // ── Lógica de delta ───────────────────────────────────────────────────────

    @Transactional
    public void handleDelta(String topic, MqttMessage message) {
        String raw = new String(message.getPayload(), StandardCharsets.UTF_8);
        try {
            JsonNode root       = objectMapper.readTree(raw);
            String deltaId      = root.path("deltaId").asText(null);
            String changeType   = root.path("changeType").asText(null);
            String actuatorType  = root.path("actuatorType").asText(null);
            String entityType    = root.path("entityType").asText(null);
            String codeName      = root.path("codeName").asText(null);
            Long   configVersion = root.hasNonNull(FIELD_CFG_VER) ? root.get(FIELD_CFG_VER).asLong() : null;
            JsonNode data        = root.path("data");

            if (deltaId == null || deltaId.isBlank()) {
                log.warn("[DeltaListener] Delta sin deltaId en topic {}", topic);
                return;
            }

            if (processedDeltaRepository.existsById(deltaId)) {
                log.info("[DeltaListener] Delta ya procesado deltaId={} — ignorado", deltaId);
                publishAck(deltaId, configVersion, "APPLIED", "Delta ya aplicado (idempotente)");
                return;
            }

            String dispatchType = actuatorType != null ? actuatorType : entityType;
            log.info("[DeltaListener] Aplicando delta deltaId={} tipo={} dispatch={} codeName={}",
                    deltaId, changeType, dispatchType, codeName);

            String errorMsg = applyDelta(changeType, dispatchType, codeName, data);

            if (errorMsg != null) {
                log.error("[DeltaListener] Error en deltaId={}: {}", deltaId, errorMsg);
                publishAck(deltaId, configVersion, "ERROR", errorMsg);
                return;
            }

            processedDeltaRepository.save(ProcessedConfigDelta.builder()
                    .deltaId(deltaId)
                    .gatewayCode(configuredGatewayId)
                    .actuatorType(actuatorType)
                    .codeName(codeName)
                    .configVersion(configVersion)
                    .build());

            updateLocalConfigVersion(configVersion);

            log.info("[DeltaListener] Delta aplicado — deltaId={} {}/{} version={}",
                    deltaId, actuatorType, codeName, configVersion);
            publishAck(deltaId, configVersion, "APPLIED", "Delta aplicado correctamente");

        } catch (Exception e) {
            log.error("[DeltaListener] Error procesando delta en topic {}: {}", topic, e.getMessage(), e);
        }
    }

    private String applyDelta(String changeType, String dispatchType, String codeName, JsonNode data) {
        if (dispatchType == null) return "actuatorType y entityType nulos";
        return switch (dispatchType.toUpperCase()) {
            case "EXTRACTOR"   -> {
                if (codeName == null) yield "codeName nulo para EXTRACTOR";
                yield applyExtractorDelta(changeType, codeName, data);
            }
            case "CRIADORA"    -> {
                if (codeName == null) yield "codeName nulo para CRIADORA";
                yield applyCriadoraDelta(changeType, codeName, data);
            }
            case "BOMBA"       -> {
                if (codeName == null) yield "codeName nulo para BOMBA";
                yield applyBombaDelta(changeType, codeName, data);
            }
            case "ALARM_RULE"  -> applyAlarmRuleDelta(changeType, codeName, data);
            case "ALARM"       -> delegateAlarmState(changeType, data);
            case "MORTALITY_RECORD"    -> delegateMortalityDelta(changeType, data);
            case "WEIGHT_RECORD"       -> delegateWeightDelta(changeType, data);
            case "CONSUMPTION_RECORD"  -> delegateConsumptionDelta(changeType, data);
            default -> "Tipo desconocido: " + dispatchType;
        };
    }

    private String delegateAlarmState(String changeType, JsonNode data) {
        if (productiveRecordDeltaService == null) return PRODUCTIVE_SVC_MISSING;
        return productiveRecordDeltaService.applyAlarmStateDelta(changeType, data);
    }

    private String delegateMortalityDelta(String changeType, JsonNode data) {
        if (productiveRecordDeltaService == null) return PRODUCTIVE_SVC_MISSING;
        return productiveRecordDeltaService.applyMortalityRecordDelta(changeType, data);
    }

    private String delegateWeightDelta(String changeType, JsonNode data) {
        if (productiveRecordDeltaService == null) return PRODUCTIVE_SVC_MISSING;
        return productiveRecordDeltaService.applyWeightRecordDelta(changeType, data);
    }

    private String delegateConsumptionDelta(String changeType, JsonNode data) {
        if (productiveRecordDeltaService == null) return PRODUCTIVE_SVC_MISSING;
        return productiveRecordDeltaService.applyConsumptionRecordDelta(changeType, data);
    }

    private String applyExtractorDelta(String changeType, String codeName, JsonNode data) {
        Extractor ext = extractorRepository.findByCodeName(codeName).orElse(null);
        if (ext == null) return "Extractor no encontrado: codeName=" + codeName;

        if (CONFIG_UPDATE.equals(changeType)) {
            if (data.hasNonNull("name"))       ext.setName(data.get("name").asText());
            if (data.hasNonNull(FIELD_ENABLED)) ext.setEnabled(data.get(FIELD_ENABLED).asBoolean());
            extractorRepository.save(ext);
        } else if (PROG_UPDATE.equals(changeType)) {
            ExtractorProgramming prog = extractorProgrammingRepository
                    .findByExtractorId(ext.getId()).orElse(null);
            if (prog == null) return "Programación de Extractor no encontrada: " + codeName;
            double tempOn  = data.hasNonNull(FIELD_TEMP_ON)  ? data.get(FIELD_TEMP_ON).asDouble()  : prog.getTemperatureOn();
            double tempOff = data.hasNonNull(FIELD_TEMP_OFF) ? data.get(FIELD_TEMP_OFF).asDouble() : prog.getTemperatureOff();
            prog.update(tempOn, tempOff);
            extractorProgrammingRepository.save(prog);
        }
        return null;
    }

    private String applyCriadoraDelta(String changeType, String codeName, JsonNode data) {
        Criadora cri = criadoraRepository.findByCodeName(codeName).orElse(null);
        if (cri == null) return "Criadora no encontrada: codeName=" + codeName;

        if (CONFIG_UPDATE.equals(changeType)) {
            if (data.hasNonNull("name"))       cri.setName(data.get("name").asText());
            if (data.hasNonNull(FIELD_ENABLED)) cri.setEnabled(data.get(FIELD_ENABLED).asBoolean());
            criadoraRepository.save(cri);
        } else if (PROG_UPDATE.equals(changeType)) {
            CriadoraProgramming prog = criadoraProgrammingRepository
                    .findByCriadoraId(cri.getId()).orElse(null);
            if (prog == null) return "Programación de Criadora no encontrada: " + codeName;
            double tempOn  = data.hasNonNull(FIELD_TEMP_ON)  ? data.get(FIELD_TEMP_ON).asDouble()  : prog.getTemperatureOn();
            double tempOff = data.hasNonNull(FIELD_TEMP_OFF) ? data.get(FIELD_TEMP_OFF).asDouble() : prog.getTemperatureOff();
            prog.update(tempOn, tempOff);
            criadoraProgrammingRepository.save(prog);
        }
        return null;
    }

    private String applyBombaDelta(String changeType, String codeName, JsonNode data) {
        Bomba bomba = bombaRepository.findByCodeName(codeName).orElse(null);
        if (bomba == null) return "Bomba no encontrada: codeName=" + codeName;

        if (CONFIG_UPDATE.equals(changeType)) {
            if (data.hasNonNull("name"))       bomba.setName(data.get("name").asText());
            if (data.hasNonNull(FIELD_ENABLED)) bomba.setEnabled(data.get(FIELD_ENABLED).asBoolean());
            bombaRepository.save(bomba);
        } else if (PROG_UPDATE.equals(changeType)) {
            BombaProgramming prog = bombaProgrammingRepository
                    .findByBombaId(bomba.getId()).orElse(null);
            if (prog == null) return "Programación de Bomba no encontrada: " + codeName;
            double tempOn  = data.hasNonNull(FIELD_TEMP_ON)  ? data.get(FIELD_TEMP_ON).asDouble()  : prog.getTemperatureOn();
            double tempOff = data.hasNonNull(FIELD_TEMP_OFF) ? data.get(FIELD_TEMP_OFF).asDouble() : prog.getTemperatureOff();
            int    dur     = data.hasNonNull("workDurationSeconds")
                    ? data.get("workDurationSeconds").asInt() : prog.getWorkDurationSeconds();
            prog.update(tempOn, tempOff, dur);
            bombaProgrammingRepository.save(prog);
        }
        return null;
    }

    private String applyAlarmRuleDelta(String changeType, String codeName, JsonNode data) {
        if (codeName == null || codeName.isBlank()) return "codeName (nombre) de regla nulo";
        AlarmRule existing = alarmRuleRepository.findByName(codeName).orElse(null);
        if (ALARM_RULE_CREATED.equals(changeType) || ALARM_RULE_UPDATED.equals(changeType)) {
            return upsertAlarmRule(existing, codeName, data);
        } else if (ALARM_RULE_ENABLED.equals(changeType) || ALARM_RULE_DISABLED.equals(changeType)) {
            if (existing == null) return "Regla de alarma no encontrada: " + codeName;
            existing.setActive(ALARM_RULE_ENABLED.equals(changeType));
            alarmRuleRepository.save(existing);
        }
        return null;
    }

    private String upsertAlarmRule(AlarmRule existing, String codeName, JsonNode data) {
        try {
            String varStr = resolveStr(data, FIELD_VARIABLE,  existing != null ? existing.getVariable().name()          : null);
            String conStr = resolveStr(data, FIELD_CONDITION, existing != null ? existing.getConditionType().name()     : null);
            String sevStr = resolveStr(data, FIELD_SEVERITY,  existing != null ? existing.getSeverity().name()          : null);
            AlarmVariable  variable   = parseEnum(AlarmVariable.class,  varStr, FIELD_VARIABLE);
            AlarmCondition condition  = parseEnum(AlarmCondition.class, conStr, FIELD_CONDITION);
            AlarmSeverity  severity   = parseEnum(AlarmSeverity.class,  sevStr, FIELD_SEVERITY);
            double  threshold   = resolveDouble(data, FIELD_THRESHOLD,    existing != null ? existing.getThreshold()             : 0.0);
            String  unit        = resolveStr(   data, FIELD_UNIT,         existing != null ? existing.getUnit()                  : "");
            int     minDuration = resolveInt(   data, FIELD_MIN_DURATION, existing != null ? existing.getMinimumDurationSeconds() : 0);
            String  message     = resolveStr(   data, FIELD_MESSAGE,      existing != null ? existing.getMessage()               : "");
            boolean active      = resolveBool(  data, FIELD_ACTIVE,       existing == null || existing.isActive());
            if (existing != null) {
                existing.update(codeName, variable, condition, threshold, unit, minDuration, severity, message);
                alarmRuleRepository.save(existing);
            } else {
                alarmRuleRepository.save(new AlarmRule(codeName, variable, condition, threshold, unit, minDuration, severity, message, active));
            }
            return null;
        } catch (IllegalArgumentException e) {
            return "Valor inválido en delta de regla de alarma: " + e.getMessage();
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String fieldName) {
        if (value == null) throw new IllegalArgumentException(fieldName + " es requerido");
        try { return Enum.valueOf(type, value.toUpperCase()); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException(fieldName + " inválido: " + value); }
    }

    private static String  resolveStr(   JsonNode d, String f, String  fallback) { return d.hasNonNull(f) ? d.get(f).asText()      : fallback; }
    private static double  resolveDouble(JsonNode d, String f, double  fallback) { return d.hasNonNull(f) ? d.get(f).asDouble()    : fallback; }
    private static int     resolveInt(   JsonNode d, String f, int     fallback) { return d.hasNonNull(f) ? d.get(f).asInt()       : fallback; }
    private static boolean resolveBool(  JsonNode d, String f, boolean fallback) { return d.hasNonNull(f) ? d.get(f).asBoolean()   : fallback; }

    private void updateLocalConfigVersion(Long configVersion) {
        if (configVersion == null) return;
        LocalConfigState state = configStateRepository
                .findByGatewayCode(configuredGatewayId)
                .orElseGet(() -> LocalConfigState.builder()
                        .gatewayCode(configuredGatewayId)
                        .galponId(configuredGalponId)
                        .configVersion(0L)
                        .build());
        if (configVersion > state.getConfigVersion()) {
            state.setConfigVersion(configVersion);
            state.setLastDeltaAppliedAt(OffsetDateTime.now());
            configStateRepository.save(state);
        }
    }

    private void publishAck(String deltaId, Long configVersion, String status, String msgText) {
        String ackTopic = GW_TOPIC_BASE + configuredGatewayId + "/config/delta/ack";
        try {
            Map<String, Object> ack = new LinkedHashMap<>();
            ack.put("deltaId",      deltaId);
            ack.put("gatewayCode",  configuredGatewayId);
            ack.put("galponId",     configuredGalponId);
            ack.put(FIELD_CFG_VER,  configVersion);
            ack.put("status",       status);
            ack.put(FIELD_MESSAGE,  msgText);
            ack.put("appliedAt",    OffsetDateTime.now().toString());
            String json = objectMapper.writeValueAsString(ack);
            publishOrQueue(ackTopic, json);
        } catch (Exception e) {
            log.error("[DeltaListener] Error publicando ACK para deltaId={}: {}", deltaId, e.getMessage());
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
            log.warn("[DeltaListener] MQTT no disponible para ACK, encolando: {}", e.getMessage());
            if (outboxService != null) {
                outboxService.enqueue(topic, json, "CONFIG_DELTA_ACK");
            }
        }
    }

    private void ensureConnected() throws Exception {
        if (client != null && !client.isConnected()) {
            client.connect(buildConnectOptions());
        }
    }

    private String deltaTopic() {
        return GW_TOPIC_BASE + configuredGatewayId + "/config/delta";
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

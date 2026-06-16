package com.avimax.backend.service;

import com.avimax.backend.config.MqttProperties;
import com.avimax.backend.dto.ConfigureBombaProgrammingRequest;
import com.avimax.backend.dto.ConfigureCriadoraProgrammingRequest;
import com.avimax.backend.dto.ConfigureExtractorProgrammingRequest;
import com.avimax.backend.entity.ProcessedProgrammingConfig;
import com.avimax.backend.repository.BombaProgrammingRepository;
import com.avimax.backend.repository.ProcessedProgrammingConfigRepository;
import com.fasterxml.jackson.databind.JsonNode;
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

@Service
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true", matchIfMissing = false)
public class MqttProgrammingListenerService {

    private static final Logger log = LoggerFactory.getLogger(MqttProgrammingListenerService.class);
    private static final int QOS = 1;
    private static final boolean RETAINED = false;
    private static final String STATUS_APPLIED           = "APPLIED";
    private static final String STATUS_ERROR             = "ERROR";
    private static final int DEFAULT_WORK_DURATION_SECONDS = 60;
    private static final String FIELD_CONFIG_ID           = "configId";
    private static final String FIELD_GALPON_ID           = "galponId";
    private static final String FIELD_ACTUATOR_ID         = "actuatorId";        // backward compat
    private static final String FIELD_CENTRAL_ACTUATOR_ID = "centralActuatorId";
    private static final String FIELD_LOCAL_ACTUATOR_ID   = "localActuatorId";

    /** Agrupa los campos del ACK de programación para reducir la cantidad de parámetros. */
    private record ProgrammingAck(
            Long configId, Long galponId, String actuatorType,
            Long centralActuatorId, String codeName, Long localActuatorId,
            String status, String message
    ) {}

    private final MqttProperties mqttProperties;
    private final ObjectMapper objectMapper;

    @Value("${app.galpon-id:1}")
    private long configuredGalponId;

    @Value("${app.gateway-id:raspi5-galpon-01}")
    private String configuredGatewayId;

    @Autowired private ExtractorService extractorService;
    @Autowired private CriadoraService criadoraService;
    @Autowired private BombaService bombaService;
    @Autowired private BombaProgrammingRepository bombaProgrammingRepository;
    @Autowired private ProcessedProgrammingConfigRepository processedConfigRepository;
    @Autowired private LocalMqttOutboxService outboxService;
    @Autowired private ActuatorControlService actuatorControlService;

    private MqttClient client;

    public MqttProgrammingListenerService(MqttProperties mqttProperties, ObjectMapper objectMapper) {
        this.mqttProperties = mqttProperties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void start() {
        try {
            client = new MqttClient(mqttProperties.brokerUrl(),
                    mqttProperties.clientId() + "-prog-listener", new MemoryPersistence());
            client.connect(buildConnectOptions());
            String topic = "avicola/galpon/" + configuredGalponId + "/config/programming";
            client.subscribe(topic, QOS, this::handleMessage);
            log.info("[ProgListener] Suscrito a {} en {}", topic, mqttProperties.brokerUrl());
        } catch (Exception e) {
            log.error("[ProgListener] No se pudo iniciar: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        try {
            if (client != null && client.isConnected()) client.disconnect();
            if (client != null) client.close();
        } catch (Exception e) {
            log.warn("[ProgListener] Error al cerrar: {}", e.getMessage());
        }
    }

    private void handleMessage(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        try {
            processConfig(objectMapper.readTree(payload));
        } catch (Exception e) {
            log.error("[ProgListener] JSON inválido en topic {}: {}", topic, e.getMessage());
        }
    }

    private void processConfig(JsonNode root) {
        Long configId          = root.hasNonNull(FIELD_CONFIG_ID)   ? root.get(FIELD_CONFIG_ID).asLong()   : null;
        Long galponId          = root.hasNonNull(FIELD_GALPON_ID)   ? root.get(FIELD_GALPON_ID).asLong()   : null;
        String upperType       = root.path("actuatorType").asText(null);
        Long centralActuatorId = root.hasNonNull(FIELD_ACTUATOR_ID) ? root.get(FIELD_ACTUATOR_ID).asLong() : null;
        String rawCodeName     = root.path("codeName").asText(null);
        String codeName        = (rawCodeName != null && !rawCodeName.isBlank()) ? rawCodeName : null;
        Double tempOn          = root.hasNonNull("temperatureOn")  ? root.get("temperatureOn").asDouble()  : null;
        Double tempOff         = root.hasNonNull("temperatureOff") ? root.get("temperatureOff").asDouble() : null;
        Integer wds            = root.hasNonNull("workDurationSeconds")
                ? root.get("workDurationSeconds").asInt() : null;

        if (galponId == null || galponId != configuredGalponId) {
            log.warn("[ProgListener] galponId={} no coincide con este backend ({}). Ignorando config={}",
                    galponId, configuredGalponId, configId);
            return;
        }

        if (isPayloadIncomplete(upperType, tempOn, tempOff)) {
            log.warn("[ProgListener] Payload incompleto en config={}", configId);
            publishAck(new ProgrammingAck(configId, galponId, null, centralActuatorId, codeName, null,
                    STATUS_ERROR, "Payload incompleto: faltan actuatorType, temperatureOn o temperatureOff"));
            return;
        }
        upperType = upperType.toUpperCase();

        if (isDuplicateConfig(configId, galponId, upperType, centralActuatorId, codeName)) return;

        // ── Resolución local por codeName (fallback: centralActuatorId) ──────────────
        Long localActuatorId;
        try {
            localActuatorId = actuatorControlService.resolveLocalActuatorId(upperType, centralActuatorId, codeName);
        } catch (IllegalArgumentException e) {
            log.warn("[ProgListener] No se pudo resolver actuador para config={}: {}", configId, e.getMessage());
            publishAck(new ProgrammingAck(configId, galponId, upperType,
                    centralActuatorId, codeName, null, STATUS_ERROR, e.getMessage()));
            return;
        }

        log.info("[ProgListener] Resuelto: centralId={} codeName={} → localId={} | config={} tempOn={} tempOff={}",
                centralActuatorId, codeName, localActuatorId, configId, tempOn, tempOff);

        String ackMessage;
        String ackStatus;
        try {
            applyProgramming(upperType, localActuatorId, tempOn, tempOff, wds);
            ackMessage = "Programación aplicada en " + upperType + " " + (codeName != null ? codeName : localActuatorId);
            ackStatus  = STATUS_APPLIED;
        } catch (Exception e) {
            log.error("[ProgListener] Error aplicando programación {} localId={}: {}", upperType, localActuatorId, e.getMessage());
            ackMessage = e.getMessage();
            ackStatus  = STATUS_ERROR;
        }
        saveProcessedConfig(configId, galponId, upperType, localActuatorId, ackStatus, ackMessage);
        publishAck(new ProgrammingAck(configId, galponId, upperType,
                centralActuatorId, codeName, localActuatorId, ackStatus, ackMessage));
    }

    private boolean isDuplicateConfig(Long configId, Long galponId, String upperType,
                                       Long centralActuatorId, String codeName) {
        if (configId == null) return false;
        return processedConfigRepository.findById(configId).map(rec -> {
            rec.setLastSeenAt(OffsetDateTime.now());
            processedConfigRepository.save(rec);
            log.info("[ProgListener] Config duplicada configId={} — reenviando ACK sin reaplicar", configId);
            publishAck(new ProgrammingAck(configId, galponId, upperType,
                    centralActuatorId, codeName, rec.getActuatorId(), rec.getStatus(), rec.getMessage()));
            return true;
        }).orElse(false);
    }

    private static boolean isPayloadIncomplete(String upperType, Double tempOn, Double tempOff) {
        return upperType == null || tempOn == null || tempOff == null;
    }

    private void saveProcessedConfig(Long configId, Long galponId, String upperType,
                                      Long localActuatorId, String status, String message) {
        if (configId == null) return;
        OffsetDateTime now = OffsetDateTime.now();
        processedConfigRepository.save(ProcessedProgrammingConfig.builder()
                .configId(configId)
                .galponId(galponId)
                .actuatorType(upperType)
                .actuatorId(localActuatorId) // guardamos el ID local resuelto
                .status(status)
                .message(message)
                .firstProcessedAt(now)
                .lastSeenAt(now)
                .build());
    }

    private void applyProgramming(String upperType, Long localActuatorId,
                                   Double tempOn, Double tempOff, Integer wds) {
        switch (upperType) {
            case "EXTRACTOR" ->
                    extractorService.configureProgramming(localActuatorId,
                            new ConfigureExtractorProgrammingRequest(tempOn, tempOff));
            case "CRIADORA" ->
                    criadoraService.configureProgramming(localActuatorId,
                            new ConfigureCriadoraProgrammingRequest(tempOn, tempOff));
            case "BOMBA" -> {
                int resolvedWds = resolveWorkDurationSeconds(localActuatorId, wds);
                bombaService.configureProgramming(localActuatorId,
                        new ConfigureBombaProgrammingRequest(tempOn, tempOff, resolvedWds));
            }
            default -> throw new IllegalArgumentException("actuatorType desconocido: " + upperType);
        }
    }

    private int resolveWorkDurationSeconds(Long localBombaId, Integer wdsFromPayload) {
        if (wdsFromPayload != null && wdsFromPayload > 0) return wdsFromPayload;
        return bombaProgrammingRepository.findByBombaId(localBombaId)
                .map(p -> p.getWorkDurationSeconds() != null ? p.getWorkDurationSeconds() : DEFAULT_WORK_DURATION_SECONDS)
                .orElse(DEFAULT_WORK_DURATION_SECONDS);
    }

    private void publishAck(ProgrammingAck a) {
        String topic = "avicola/galpon/" + a.galponId() + "/config/programming/ack";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(FIELD_CONFIG_ID, a.configId());
        payload.put(FIELD_GALPON_ID, a.galponId());
        payload.put("gatewayId", configuredGatewayId);
        payload.put("actuatorType", a.actuatorType());
        payload.put(FIELD_ACTUATOR_ID, a.centralActuatorId());      // backward compat
        payload.put(FIELD_CENTRAL_ACTUATOR_ID, a.centralActuatorId());
        payload.put(FIELD_LOCAL_ACTUATOR_ID, a.localActuatorId());
        payload.put("codeName", a.codeName());
        payload.put("status", a.status());
        payload.put("message", a.message());
        payload.put("appliedAt", OffsetDateTime.now().toString());

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("[ProgListener] No se pudo serializar ACK para config={}: {}", a.configId(), e.getMessage());
            return;
        }
        try {
            ensureConnected();
            MqttMessage msg = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
            msg.setQos(QOS);
            msg.setRetained(RETAINED);
            client.publish(topic, msg);
            log.info("[ProgListener] ACK {} publicado — configId={} localId={} codeName={}",
                    a.status(), a.configId(), a.localActuatorId(), a.codeName());
        } catch (Exception e) {
            log.warn("[ProgListener] Falló publicación de ACK para config={}, encolando: {}", a.configId(), e.getMessage());
            outboxService.enqueue(topic, json, "PROGRAMMING_ACK");
        }
    }

    private void ensureConnected() throws Exception {
        if (client == null) throw new IllegalStateException("Cliente MQTT no inicializado");
        if (!client.isConnected()) client.connect(buildConnectOptions());
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

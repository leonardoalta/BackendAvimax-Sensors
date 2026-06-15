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
    private static final String STATUS_APPLIED    = "APPLIED";
    private static final String STATUS_ERROR      = "ERROR";
    private static final int DEFAULT_WORK_DURATION_SECONDS = 60;
    private static final String FIELD_CONFIG_ID   = "configId";
    private static final String FIELD_GALPON_ID   = "galponId";
    private static final String FIELD_ACTUATOR_ID = "actuatorId";

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
            MqttConnectOptions options = buildConnectOptions();
            client.connect(options);
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
            JsonNode root = objectMapper.readTree(payload);
            processConfig(root);
        } catch (Exception e) {
            log.error("[ProgListener] JSON inválido en topic {}: {}", topic, e.getMessage());
        }
    }

    private void processConfig(JsonNode root) {
        Long configId   = root.hasNonNull(FIELD_CONFIG_ID)   ? root.get(FIELD_CONFIG_ID).asLong()   : null;
        Long galponId   = root.hasNonNull(FIELD_GALPON_ID)   ? root.get(FIELD_GALPON_ID).asLong()   : null;
        String upperType = root.path("actuatorType").asText(null);
        Long actuatorId  = root.hasNonNull(FIELD_ACTUATOR_ID) ? root.get(FIELD_ACTUATOR_ID).asLong() : null;
        Double tempOn    = root.hasNonNull("temperatureOn")  ? root.get("temperatureOn").asDouble()  : null;
        Double tempOff   = root.hasNonNull("temperatureOff") ? root.get("temperatureOff").asDouble() : null;
        Integer wds      = root.hasNonNull("workDurationSeconds")
                ? root.get("workDurationSeconds").asInt() : null;

        if (galponId == null || galponId != configuredGalponId) {
            log.warn("[ProgListener] galponId {} no coincide con este backend ({}). Ignorando config {}",
                    galponId, configuredGalponId, configId);
            return;
        }

        if (upperType == null || actuatorId == null || tempOn == null || tempOff == null) {
            log.warn("[ProgListener] Payload incompleto en config {} — ignorando", configId);
            publishAck(configId, galponId, upperType, actuatorId, STATUS_ERROR,
                    "Payload incompleto: faltan actuatorType, actuatorId, temperatureOn o temperatureOff");
            return;
        }

        upperType = upperType.toUpperCase();

        if (isDuplicateConfig(configId, galponId, upperType, actuatorId)) return;

        log.info("[ProgListener] Configuración recibida: {} {} tempOn={} tempOff={}", upperType, actuatorId, tempOn, tempOff);

        String ackMessage;
        String ackStatus;
        try {
            applyProgramming(upperType, actuatorId, tempOn, tempOff, wds);
            ackMessage = "Programación aplicada en " + upperType + " " + actuatorId;
            ackStatus  = STATUS_APPLIED;
        } catch (Exception e) {
            log.error("[ProgListener] Error aplicando programación {} {}: {}", upperType, actuatorId, e.getMessage());
            ackMessage = e.getMessage();
            ackStatus  = STATUS_ERROR;
        }
        saveProcessedConfig(configId, galponId, upperType, actuatorId, ackStatus, ackMessage);
        publishAck(configId, galponId, upperType, actuatorId, ackStatus, ackMessage);
    }

    private boolean isDuplicateConfig(Long configId, Long galponId, String upperType, Long actuatorId) {
        if (configId == null) return false;
        return processedConfigRepository.findById(configId).map(rec -> {
            rec.setLastSeenAt(OffsetDateTime.now());
            processedConfigRepository.save(rec);
            log.info("[ProgListener] Config duplicada configId={} — republicando ACK sin reaplicar", configId);
            publishAck(configId, galponId, upperType, actuatorId, rec.getStatus(), rec.getMessage());
            return true;
        }).orElse(false);
    }

    private void saveProcessedConfig(Long configId, Long galponId, String upperType,
                                      Long actuatorId, String status, String message) {
        if (configId == null) return;
        OffsetDateTime now = OffsetDateTime.now();
        processedConfigRepository.save(ProcessedProgrammingConfig.builder()
                .configId(configId)
                .galponId(galponId)
                .actuatorType(upperType)
                .actuatorId(actuatorId)
                .status(status)
                .message(message)
                .firstProcessedAt(now)
                .lastSeenAt(now)
                .build());
    }

    private void applyProgramming(String upperType, Long actuatorId,
                                   Double tempOn, Double tempOff, Integer wds) {
        switch (upperType) {
            case "EXTRACTOR" ->
                    extractorService.configureProgramming(actuatorId,
                            new ConfigureExtractorProgrammingRequest(tempOn, tempOff));
            case "CRIADORA" ->
                    criadoraService.configureProgramming(actuatorId,
                            new ConfigureCriadoraProgrammingRequest(tempOn, tempOff));
            case "BOMBA" -> {
                int resolvedWds = resolveWorkDurationSeconds(actuatorId, wds);
                bombaService.configureProgramming(actuatorId,
                        new ConfigureBombaProgrammingRequest(tempOn, tempOff, resolvedWds));
            }
            default -> throw new IllegalArgumentException("actuatorType desconocido: " + upperType);
        }
    }

    private int resolveWorkDurationSeconds(Long bombaId, Integer wdsFromPayload) {
        if (wdsFromPayload != null && wdsFromPayload > 0) {
            return wdsFromPayload;
        }
        return bombaProgrammingRepository.findByBombaId(bombaId)
                .map(p -> p.getWorkDurationSeconds() != null ? p.getWorkDurationSeconds() : DEFAULT_WORK_DURATION_SECONDS)
                .orElse(DEFAULT_WORK_DURATION_SECONDS);
    }

    private void publishAck(Long configId, Long galponId, String actuatorType, Long actuatorId,
                             String status, String message) {
        String topic = "avicola/galpon/" + galponId + "/config/programming/ack";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(FIELD_CONFIG_ID, configId);
        payload.put(FIELD_GALPON_ID, galponId);
        payload.put("gatewayId", configuredGatewayId);
        payload.put("actuatorType", actuatorType);
        payload.put(FIELD_ACTUATOR_ID, actuatorId);
        payload.put("status", status);
        payload.put("message", message);
        payload.put("appliedAt", OffsetDateTime.now().toString());

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("[ProgListener] No se pudo serializar ACK para config {}: {}", configId, e.getMessage());
            return;
        }
        try {
            ensureConnected();
            MqttMessage msg = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
            msg.setQos(QOS);
            msg.setRetained(RETAINED);
            client.publish(topic, msg);
            log.info("[ProgListener] ACK {} publicado — configId={}", status, configId);
        } catch (Exception e) {
            log.warn("[ProgListener] Falló publicación de ACK para config {}, encolando: {}", configId, e.getMessage());
            outboxService.enqueue(topic, json, "PROGRAMMING_ACK");
        }
    }

    private void ensureConnected() throws Exception {
        if (client == null) throw new IllegalStateException("Cliente MQTT no inicializado");
        if (!client.isConnected()) {
            client.connect(buildConnectOptions());
        }
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

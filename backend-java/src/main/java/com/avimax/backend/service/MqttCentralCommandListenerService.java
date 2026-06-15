package com.avimax.backend.service;

import com.avimax.backend.config.MqttProperties;
import com.avimax.backend.entity.ProcessedMqttCommand;
import com.avimax.backend.repository.ProcessedMqttCommandRepository;
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
public class MqttCentralCommandListenerService {

    private static final Logger log = LoggerFactory.getLogger(MqttCentralCommandListenerService.class);
    private static final int QOS = 1;
    private static final boolean RETAINED = false;
    private static final String FIELD_COMMAND_ID  = "commandId";
    private static final String FIELD_GALPON_ID   = "galponId";
    private static final String FIELD_ACTUATOR_ID = "actuatorId";

    private final MqttProperties mqttProperties;
    private final ActuatorControlService actuatorControlService;
    private final ObjectMapper objectMapper;

    @Autowired private ProcessedMqttCommandRepository processedCommandRepository;
    @Autowired private LocalMqttOutboxService outboxService;

    @Value("${app.galpon-id:1}")
    private long configuredGalponId;

    @Value("${app.gateway-id:raspi5-galpon-01}")
    private String configuredGatewayId;

    private MqttClient client;

    public MqttCentralCommandListenerService(MqttProperties mqttProperties,
                                              ActuatorControlService actuatorControlService,
                                              ObjectMapper objectMapper) {
        this.mqttProperties = mqttProperties;
        this.actuatorControlService = actuatorControlService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void start() {
        try {
            client = new MqttClient(mqttProperties.brokerUrl(),
                    mqttProperties.clientId() + "-cmd-listener", new MemoryPersistence());
            MqttConnectOptions options = buildConnectOptions();
            client.connect(options);
            String topic = "avicola/galpon/" + configuredGalponId + "/actuadores/cmd";
            client.subscribe(topic, QOS, this::handleMessage);
            log.info("[CmdListener] Suscrito a {} en {}", topic, mqttProperties.brokerUrl());
        } catch (Exception e) {
            log.warn("[CmdListener] No se pudo conectar al iniciar (se reintentará cuando haya conexión): {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        try {
            if (client != null && client.isConnected()) client.disconnect();
            if (client != null) client.close();
        } catch (Exception e) {
            log.warn("[CmdListener] Error al cerrar: {}", e.getMessage());
        }
    }

    private void handleMessage(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        try {
            processCommand(objectMapper.readTree(payload));
        } catch (Exception e) {
            log.error("[CmdListener] JSON inválido en topic {}: {}", topic, e.getMessage());
        }
    }

    private void processCommand(JsonNode root) throws Exception {
        Long commandId  = root.hasNonNull(FIELD_COMMAND_ID)  ? root.get(FIELD_COMMAND_ID).asLong()  : null;
        Long galponId   = root.hasNonNull(FIELD_GALPON_ID)   ? root.get(FIELD_GALPON_ID).asLong()   : null;
        String actuatorType = root.path("actuatorType").asText(null);
        Long actuatorId     = root.hasNonNull(FIELD_ACTUATOR_ID) ? root.get(FIELD_ACTUATOR_ID).asLong() : null;
        String action       = root.path("action").asText(null);
        Integer workDurationSeconds = root.hasNonNull("workDurationSeconds")
                ? root.get("workDurationSeconds").asInt() : null;

        if (galponId == null || galponId != configuredGalponId) {
            log.warn("[CmdListener] galponId {} no coincide con este backend ({}). Ignorando comando {}",
                    galponId, configuredGalponId, commandId);
            return;
        }

        if (actuatorType == null || actuatorType.isBlank()) {
            log.warn("[CmdListener] actuatorType ausente en comando {}", commandId);
            publishResponse(commandId, galponId, actuatorType, actuatorId, action,
                    "ERROR", "actuatorType ausente");
            return;
        }
        String upperType = actuatorType.toUpperCase();
        if (!upperType.equals(ActuatorControlService.TYPE_EXTRACTOR)
                && !upperType.equals(ActuatorControlService.TYPE_CRIADORA)
                && !upperType.equals(ActuatorControlService.TYPE_BOMBA)) {
            log.warn("[CmdListener] Tipo desconocido: {} en comando {}", actuatorType, commandId);
            publishResponse(commandId, galponId, actuatorType, actuatorId, action,
                    "ERROR", "Tipo desconocido: " + actuatorType);
            return;
        }

        if (action == null || (!action.equalsIgnoreCase("ON") && !action.equalsIgnoreCase("OFF"))) {
            log.warn("[CmdListener] Acción inválida '{}' en comando {}", action, commandId);
            return;
        }

        if (isDuplicateCommand(commandId, galponId, actuatorType, actuatorId, action)) return;

        String responseMessage = "Comando " + action + " aplicado en " + upperType + " " + actuatorId;
        actuatorControlService.applyExternalCommand(upperType, actuatorId, action,
                "CENTRAL_COMMAND", workDurationSeconds);
        saveProcessedCommand(commandId, galponId, upperType, actuatorId, action, responseMessage);
        publishResponse(commandId, galponId, actuatorType, actuatorId, action, "EXECUTED", responseMessage);
    }

    private boolean isDuplicateCommand(Long commandId, Long galponId,
                                        String actuatorType, Long actuatorId, String action) {
        if (commandId == null) return false;
        return processedCommandRepository.findById(commandId).map(rec -> {
            rec.setLastSeenAt(OffsetDateTime.now());
            processedCommandRepository.save(rec);
            log.info("[CmdListener] Comando duplicado commandId={} — republicando respuesta sin reaplicar", commandId);
            publishResponse(commandId, galponId, actuatorType, actuatorId, action, rec.getStatus(), rec.getMessage());
            return true;
        }).orElse(false);
    }

    private void saveProcessedCommand(Long commandId, Long galponId, String upperType,
                                       Long actuatorId, String action, String message) {
        if (commandId == null) return;
        OffsetDateTime now = OffsetDateTime.now();
        processedCommandRepository.save(ProcessedMqttCommand.builder()
                .commandId(commandId)
                .galponId(galponId)
                .actuatorType(upperType)
                .actuatorId(actuatorId)
                .action(action.toUpperCase())
                .status("EXECUTED")
                .message(message)
                .firstProcessedAt(now)
                .lastSeenAt(now)
                .build());
    }

    private void publishResponse(Long commandId, Long galponId, String actuatorType,
                                  Long actuatorId, String action, String status, String message) {
        String topic = "avicola/galpon/" + galponId + "/actuadores/respuestas";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(FIELD_COMMAND_ID, commandId);
        payload.put(FIELD_GALPON_ID, galponId);
        payload.put("gatewayId", configuredGatewayId);
        payload.put("actuatorType", actuatorType);
        payload.put(FIELD_ACTUATOR_ID, actuatorId);
        payload.put("action", action);
        payload.put("status", status);
        payload.put("message", message);
        payload.put("executedAt", OffsetDateTime.now().toString());

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("[CmdListener] No se pudo serializar respuesta para comando {}: {}", commandId, e.getMessage());
            return;
        }
        try {
            ensureConnected();
            MqttMessage msg = new MqttMessage(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            msg.setQos(QOS);
            msg.setRetained(RETAINED);
            client.publish(topic, msg);
            log.info("[CmdListener] Respuesta publicada — commandId={}, status={}", commandId, status);
        } catch (Exception e) {
            log.warn("[CmdListener] Falló publicación de respuesta para comando {}, encolando: {}", commandId, e.getMessage());
            outboxService.enqueue(topic, json, "ACTUATOR_RESPONSE");
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

package com.avimax.backend.service;

import com.avimax.backend.config.MqttProperties;
import com.avimax.backend.entity.Bomba;
import com.avimax.backend.entity.ConsumptionRecord;
import com.avimax.backend.entity.Criadora;
import com.avimax.backend.entity.Extractor;
import com.avimax.backend.entity.MortalityRecord;
import com.avimax.backend.entity.WeightRecord;
import com.avimax.backend.repository.BombaRepository;
import com.avimax.backend.repository.ConsumptionRecordRepository;
import com.avimax.backend.repository.CriadoraRepository;
import com.avimax.backend.repository.ExtractorRepository;
import com.avimax.backend.repository.MortalityRecordRepository;
import com.avimax.backend.repository.WeightRecordRepository;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

/**
 * Escucha ACKs del central para eventos de sync locales.
 * Topic: avicola/galpon/{galponId}/sync/ack
 *
 * Para actuadores: actualiza centralActuatorId, lastSyncedAt, syncSource.
 * Para registros productivos (mortality, weight, consumption):
 *   actualiza centralRecordId y syncStatus = SYNCED.
 */
@Service
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true", matchIfMissing = false)
public class MqttLocalSyncAckListenerService {

    private static final Logger log = LoggerFactory.getLogger(MqttLocalSyncAckListenerService.class);
    private static final int    QOS          = 1;
    private static final String SYNC_SYNCED  = "SYNCED";
    private static final String SYNC_SOURCE  = "LOCAL";

    private final MqttProperties              mqttProperties;
    private final ObjectMapper                objectMapper;
    private final ExtractorRepository         extractorRepository;
    private final CriadoraRepository          criadoraRepository;
    private final BombaRepository             bombaRepository;
    private final MortalityRecordRepository   mortalityRecordRepository;
    private final WeightRecordRepository      weightRecordRepository;
    private final ConsumptionRecordRepository consumptionRecordRepository;

    @Value("${app.galpon-id:1}")
    private long configuredGalponId;

    private MqttClient client;

    public MqttLocalSyncAckListenerService(MqttProperties mqttProperties,
                                            ObjectMapper objectMapper,
                                            ExtractorRepository extractorRepository,
                                            CriadoraRepository criadoraRepository,
                                            BombaRepository bombaRepository,
                                            MortalityRecordRepository mortalityRecordRepository,
                                            WeightRecordRepository weightRecordRepository,
                                            ConsumptionRecordRepository consumptionRecordRepository) {
        this.mqttProperties             = mqttProperties;
        this.objectMapper               = objectMapper;
        this.extractorRepository        = extractorRepository;
        this.criadoraRepository         = criadoraRepository;
        this.bombaRepository            = bombaRepository;
        this.mortalityRecordRepository  = mortalityRecordRepository;
        this.weightRecordRepository     = weightRecordRepository;
        this.consumptionRecordRepository = consumptionRecordRepository;
    }

    @PostConstruct
    public void start() {
        try {
            client = new MqttClient(mqttProperties.brokerUrl(),
                    mqttProperties.clientId() + "-sync-ack-listener", new MemoryPersistence());
            client.connect(buildConnectOptions());
            String topic = "avicola/galpon/" + configuredGalponId + "/sync/ack";
            client.subscribe(topic, QOS, this::handleAck);
            log.info("[SyncAckListener] Suscrito a {} en {}", topic, mqttProperties.brokerUrl());
        } catch (Exception e) {
            log.error("[SyncAckListener] No se pudo iniciar: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        try {
            if (client != null && client.isConnected()) client.disconnect();
            if (client != null) client.close();
        } catch (Exception e) {
            log.warn("[SyncAckListener] Error al cerrar: {}", e.getMessage());
        }
    }

    @Transactional
    public void handleAck(String topic, MqttMessage message) {
        String raw = new String(message.getPayload(), StandardCharsets.UTF_8);
        try {
            JsonNode root        = objectMapper.readTree(raw);
            String eventType     = root.path("eventType").asText(null);
            String status        = root.path("status").asText("UNKNOWN");
            String eventId       = root.path("eventId").asText(null);
            Long centralRecordId = root.hasNonNull("centralRecordId") ? root.get("centralRecordId").asLong() : null;
            Long localEntityId   = root.hasNonNull("localEntityId")   ? root.get("localEntityId").asLong()   : null;

            log.info("[SyncAckListener] ACK recibido eventId={} eventType={} status={} centralId={} localId={}",
                    eventId, eventType, status, centralRecordId, localEntityId);

            if (!"APPLIED".equals(status)) {
                log.warn("[SyncAckListener] ACK con status={} para eventType={}: {}",
                        status, eventType, root.path("message").asText());
                return;
            }

            if (isActuatorEvent(eventType)) {
                applyActuatorAck(root, centralRecordId);
            } else if ("MORTALITY_RECORDED".equals(eventType)) {
                applyProductiveRecordAck(localEntityId, centralRecordId, "mortality");
            } else if ("WEIGHT_RECORDED".equals(eventType)) {
                applyProductiveRecordAck(localEntityId, centralRecordId, "weight");
            } else if ("CONSUMPTION_RECORDED".equals(eventType)) {
                applyProductiveRecordAck(localEntityId, centralRecordId, "consumption");
            } else {
                log.debug("[SyncAckListener] ACK recibido para eventType={} — sin acción local requerida", eventType);
            }
        } catch (Exception e) {
            log.error("[SyncAckListener] Error procesando ACK en {}: {}", topic, e.getMessage());
        }
    }

    // ── Actuadores ────────────────────────────────────────────────────────────

    private static boolean isActuatorEvent(String eventType) {
        return "ACTUATOR_CREATED".equals(eventType) || "ACTUATOR_UPDATED".equals(eventType)
                || "ACTUATOR_ENABLED".equals(eventType) || "ACTUATOR_DISABLED".equals(eventType);
    }

    private void applyActuatorAck(JsonNode root, Long centralId) {
        if (centralId == null) return;
        String actuatorType = root.path("actuatorType").asText(null);
        String codeName     = root.path("codeName").asText(null);
        if (actuatorType == null || codeName == null) return;

        switch (actuatorType.toUpperCase()) {
            case "EXTRACTOR" -> extractorRepository.findByCodeName(codeName).ifPresent(e -> updateActuatorSync(e, centralId));
            case "CRIADORA"  -> criadoraRepository.findByCodeName(codeName).ifPresent(c -> updateActuatorSync(c, centralId));
            case "BOMBA"     -> bombaRepository.findByCodeName(codeName).ifPresent(b -> updateActuatorSync(b, centralId));
            default -> log.warn("[SyncAckListener] Tipo de actuador desconocido en ACK: {}", actuatorType);
        }
    }

    private void updateActuatorSync(Extractor e, Long centralId) {
        e.setCentralActuatorId(centralId);
        e.setLastSyncedAt(OffsetDateTime.now());
        e.setSyncSource(SYNC_SOURCE);
        extractorRepository.save(e);
        log.info("[SyncAckListener] Extractor codeName={} → centralActuatorId={}", e.getCodeName(), centralId);
    }

    private void updateActuatorSync(Criadora c, Long centralId) {
        c.setCentralActuatorId(centralId);
        c.setLastSyncedAt(OffsetDateTime.now());
        c.setSyncSource(SYNC_SOURCE);
        criadoraRepository.save(c);
        log.info("[SyncAckListener] Criadora codeName={} → centralActuatorId={}", c.getCodeName(), centralId);
    }

    private void updateActuatorSync(Bomba b, Long centralId) {
        b.setCentralActuatorId(centralId);
        b.setLastSyncedAt(OffsetDateTime.now());
        b.setSyncSource(SYNC_SOURCE);
        bombaRepository.save(b);
        log.info("[SyncAckListener] Bomba codeName={} → centralActuatorId={}", b.getCodeName(), centralId);
    }

    // ── Registros productivos ─────────────────────────────────────────────────

    private void applyProductiveRecordAck(Long localEntityId, Long centralRecordId, String kind) {
        if (localEntityId == null || centralRecordId == null) {
            log.warn("[SyncAckListener] ACK de {} sin localEntityId o centralRecordId — ignorado", kind);
            return;
        }
        switch (kind) {
            case "mortality" -> mortalityRecordRepository.findById(localEntityId).ifPresentOrElse(
                    m -> { m.setCentralRecordId(centralRecordId); m.setSyncStatus(SYNC_SYNCED);
                           mortalityRecordRepository.save(m);
                           log.info("[SyncAckListener] Mortalidad localId={} → centralId={}", localEntityId, centralRecordId); },
                    () -> log.warn("[SyncAckListener] MortalityRecord localId={} no encontrado", localEntityId));
            case "weight" -> weightRecordRepository.findById(localEntityId).ifPresentOrElse(
                    w -> { w.setCentralRecordId(centralRecordId); w.setSyncStatus(SYNC_SYNCED);
                           weightRecordRepository.save(w);
                           log.info("[SyncAckListener] Peso localId={} → centralId={}", localEntityId, centralRecordId); },
                    () -> log.warn("[SyncAckListener] WeightRecord localId={} no encontrado", localEntityId));
            case "consumption" -> consumptionRecordRepository.findById(localEntityId).ifPresentOrElse(
                    c -> { c.setCentralRecordId(centralRecordId); c.setSyncStatus(SYNC_SYNCED);
                           consumptionRecordRepository.save(c);
                           log.info("[SyncAckListener] Consumo localId={} → centralId={}", localEntityId, centralRecordId); },
                    () -> log.warn("[SyncAckListener] ConsumptionRecord localId={} no encontrado", localEntityId));
            default -> log.warn("[SyncAckListener] Tipo de registro desconocido: {}", kind);
        }
    }

    // ── MQTT helpers ──────────────────────────────────────────────────────────

    private MqttConnectOptions buildConnectOptions() {
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setAutomaticReconnect(true);
        opts.setCleanSession(true);
        opts.setConnectionTimeout(mqttProperties.connectionTimeoutSeconds());
        opts.setKeepAliveInterval(mqttProperties.keepAliveSeconds());
        if (mqttProperties.username() != null && !mqttProperties.username().isBlank()) {
            opts.setUserName(mqttProperties.username());
        }
        if (mqttProperties.password() != null && !mqttProperties.password().isBlank()) {
            opts.setPassword(mqttProperties.password().toCharArray());
        }
        return opts;
    }
}

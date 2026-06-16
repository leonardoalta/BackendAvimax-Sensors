package com.avimax.backend.service;

import com.avimax.backend.entity.Alarm;
import com.avimax.backend.entity.AlarmEvent;
import com.avimax.backend.entity.AlarmEventType;
import com.avimax.backend.entity.AlarmStatus;
import com.avimax.backend.entity.ConsumptionRecord;
import com.avimax.backend.entity.MortalityRecord;
import com.avimax.backend.entity.WeightRecord;
import com.avimax.backend.repository.AlarmEventRepository;
import com.avimax.backend.repository.AlarmRepository;
import com.avimax.backend.repository.ConsumptionRecordRepository;
import com.avimax.backend.repository.MortalityRecordRepository;
import com.avimax.backend.repository.WeightRecordRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

/**
 * Aplica deltas recibidos del central que afectan registros productivos
 * y estados de alarma locales.
 *
 * Entidades manejadas:
 *  - ALARM: ALARM_ACKNOWLEDGED, ALARM_CLOSED
 *  - MORTALITY_RECORD: MORTALITY_RECORD_UPDATED, MORTALITY_RECORD_DELETED_LOGICAL
 *  - WEIGHT_RECORD: WEIGHT_RECORD_UPDATED, WEIGHT_RECORD_DELETED_LOGICAL
 *  - CONSUMPTION_RECORD: CONSUMPTION_RECORD_UPDATED, CONSUMPTION_RECORD_DELETED_LOGICAL
 */
@Service
public class LocalProductiveRecordDeltaService {

    private static final Logger log = LoggerFactory.getLogger(LocalProductiveRecordDeltaService.class);

    private static final Set<AlarmStatus> OPEN_STATUSES      = Set.of(AlarmStatus.ACTIVA, AlarmStatus.RECONOCIDA);
    private static final String SYNC_DELETED                  = "DELETED_LOGICAL";
    private static final String SYNC_SYNCED                   = "SYNCED";
    private static final String FIELD_LOCAL_RECORD_ID         = "localRecordId";
    private static final String FIELD_CENTRAL_RECORD_ID       = "centralRecordId";

    private final AlarmRepository            alarmRepository;
    private final AlarmEventRepository       alarmEventRepository;
    private final MortalityRecordRepository  mortalityRecordRepository;
    private final WeightRecordRepository     weightRecordRepository;
    private final ConsumptionRecordRepository consumptionRecordRepository;

    public LocalProductiveRecordDeltaService(AlarmRepository alarmRepository,
                                              AlarmEventRepository alarmEventRepository,
                                              MortalityRecordRepository mortalityRecordRepository,
                                              WeightRecordRepository weightRecordRepository,
                                              ConsumptionRecordRepository consumptionRecordRepository) {
        this.alarmRepository             = alarmRepository;
        this.alarmEventRepository        = alarmEventRepository;
        this.mortalityRecordRepository   = mortalityRecordRepository;
        this.weightRecordRepository      = weightRecordRepository;
        this.consumptionRecordRepository = consumptionRecordRepository;
    }

    // ── Alarmas ──────────────────────────────────────────────────────────────

    @Transactional
    public String applyAlarmStateDelta(String changeType, JsonNode data) {
        String ruleName = data.path("alarmCode").asText(null);
        if (ruleName == null || ruleName.isBlank()) {
            ruleName = data.path("ruleName").asText(null);
        }
        if (ruleName == null) return "alarmCode/ruleName requerido para delta de alarma";

        Optional<Alarm> opt = alarmRepository
                .findFirstByRuleNameAndStatusInOrderByActivatedAtDesc(ruleName, OPEN_STATUSES);
        if (opt.isEmpty()) {
            log.warn("[ProductiveDelta] Alarma abierta no encontrada para ruleName={}", ruleName);
            return null;
        }

        Alarm alarm = opt.get();
        AlarmStatus previous = alarm.getStatus();

        switch (changeType) {
            case "ALARM_ACKNOWLEDGED" -> {
                OffsetDateTime at = parseOrNow(data.path("acknowledgedAt").asText(null));
                alarm.recognize(at);
                alarmRepository.save(alarm);
                alarmEventRepository.save(new AlarmEvent(alarm, AlarmEventType.ALARMA_RECONOCIDA,
                        previous, AlarmStatus.RECONOCIDA, "Reconocida desde central", at));
                log.info("[ProductiveDelta] Alarma {} reconocida desde central, ruleName={}", alarm.getId(), ruleName);
            }
            case "ALARM_CLOSED" -> {
                OffsetDateTime at = parseOrNow(data.path("closedAt").asText(null));
                alarm.close(at);
                alarmRepository.save(alarm);
                alarmEventRepository.save(new AlarmEvent(alarm, AlarmEventType.ALARMA_CERRADA,
                        previous, AlarmStatus.CERRADA, "Cerrada desde central", at));
                log.info("[ProductiveDelta] Alarma {} cerrada desde central, ruleName={}", alarm.getId(), ruleName);
            }
            default -> log.warn("[ProductiveDelta] changeType desconocido para ALARM: {}", changeType);
        }
        return null;
    }

    // ── Mortalidad ────────────────────────────────────────────────────────────

    @Transactional
    public String applyMortalityRecordDelta(String changeType, JsonNode data) {
        MortalityRecord mortality = findMortalityRecord(data);
        if (mortality == null) return "Registro de mortalidad no encontrado en delta";

        if ("MORTALITY_RECORD_DELETED_LOGICAL".equals(changeType)) {
            mortality.setSyncStatus(SYNC_DELETED);
            mortalityRecordRepository.save(mortality);
            log.info("[ProductiveDelta] Mortalidad id={} marcada como eliminada desde central", mortality.getId());
            return null;
        }

        if (data.hasNonNull("quantity"))     mortality.setTotalCount(data.get("quantity").asInt());
        if (data.hasNonNull("observations")) mortality.setObservations(data.get("observations").asText());
        mortality.setSyncStatus(SYNC_SYNCED);
        mortalityRecordRepository.save(mortality);
        log.info("[ProductiveDelta] Mortalidad id={} actualizada desde central", mortality.getId());
        return null;
    }

    // ── Peso ──────────────────────────────────────────────────────────────────

    @Transactional
    public String applyWeightRecordDelta(String changeType, JsonNode data) {
        WeightRecord weight = findWeightRecord(data);
        if (weight == null) return "Registro de peso no encontrado en delta";

        if ("WEIGHT_RECORD_DELETED_LOGICAL".equals(changeType)) {
            weight.setSyncStatus(SYNC_DELETED);
            weightRecordRepository.save(weight);
            log.info("[ProductiveDelta] Peso id={} marcado como eliminado desde central", weight.getId());
            return null;
        }

        if (data.hasNonNull("averageWeightGrams")) weight.setAverageWeight(data.get("averageWeightGrams").asDouble());
        if (data.hasNonNull("sampleSize"))         weight.setSampledBirdsCount(data.get("sampleSize").asInt());
        weight.setSyncStatus(SYNC_SYNCED);
        weightRecordRepository.save(weight);
        log.info("[ProductiveDelta] Peso id={} actualizado desde central", weight.getId());
        return null;
    }

    // ── Consumo ───────────────────────────────────────────────────────────────

    @Transactional
    public String applyConsumptionRecordDelta(String changeType, JsonNode data) {
        ConsumptionRecord consumption = findConsumptionRecord(data);
        if (consumption == null) return "Registro de consumo no encontrado en delta";

        if ("CONSUMPTION_RECORD_DELETED_LOGICAL".equals(changeType)) {
            consumption.setSyncStatus(SYNC_DELETED);
            consumptionRecordRepository.save(consumption);
            log.info("[ProductiveDelta] Consumo id={} marcado como eliminado desde central", consumption.getId());
            return null;
        }

        if (data.hasNonNull("feedKg")) consumption.setTotalConsumptionKg(data.get("feedKg").asDouble());
        consumption.setSyncStatus(SYNC_SYNCED);
        consumptionRecordRepository.save(consumption);
        log.info("[ProductiveDelta] Consumo id={} actualizado desde central", consumption.getId());
        return null;
    }

    // ── Helpers de búsqueda ───────────────────────────────────────────────────

    private MortalityRecord findMortalityRecord(JsonNode data) {
        if (data.hasNonNull(FIELD_LOCAL_RECORD_ID)) {
            return mortalityRecordRepository.findById(data.get(FIELD_LOCAL_RECORD_ID).asLong()).orElse(null);
        }
        if (data.hasNonNull(FIELD_CENTRAL_RECORD_ID)) {
            return mortalityRecordRepository.findByCentralRecordId(data.get(FIELD_CENTRAL_RECORD_ID).asLong()).orElse(null);
        }
        return null;
    }

    private WeightRecord findWeightRecord(JsonNode data) {
        if (data.hasNonNull(FIELD_LOCAL_RECORD_ID)) {
            return weightRecordRepository.findById(data.get(FIELD_LOCAL_RECORD_ID).asLong()).orElse(null);
        }
        if (data.hasNonNull(FIELD_CENTRAL_RECORD_ID)) {
            return weightRecordRepository.findByCentralRecordId(data.get(FIELD_CENTRAL_RECORD_ID).asLong()).orElse(null);
        }
        return null;
    }

    private ConsumptionRecord findConsumptionRecord(JsonNode data) {
        if (data.hasNonNull(FIELD_LOCAL_RECORD_ID)) {
            return consumptionRecordRepository.findById(data.get(FIELD_LOCAL_RECORD_ID).asLong()).orElse(null);
        }
        if (data.hasNonNull(FIELD_CENTRAL_RECORD_ID)) {
            return consumptionRecordRepository.findByCentralRecordId(data.get(FIELD_CENTRAL_RECORD_ID).asLong()).orElse(null);
        }
        return null;
    }

    private static OffsetDateTime parseOrNow(String s) {
        if (s == null || s.isBlank()) return OffsetDateTime.now();
        try { return OffsetDateTime.parse(s); } catch (Exception e) { return OffsetDateTime.now(); }
    }
}

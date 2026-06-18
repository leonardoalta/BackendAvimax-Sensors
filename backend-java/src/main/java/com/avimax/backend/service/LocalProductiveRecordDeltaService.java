package com.avimax.backend.service;

import com.avimax.backend.entity.Alarm;
import com.avimax.backend.entity.AlarmEvent;
import com.avimax.backend.entity.AlarmEventType;
import com.avimax.backend.entity.AlarmStatus;
import com.avimax.backend.entity.ConsumptionRecord;
import com.avimax.backend.entity.Flock;
import com.avimax.backend.entity.FlockStatus;
import com.avimax.backend.entity.MortalityRecord;
import com.avimax.backend.entity.WeightRecord;
import com.avimax.backend.repository.AlarmEventRepository;
import com.avimax.backend.repository.AlarmRepository;
import com.avimax.backend.repository.ConsumptionRecordRepository;
import com.avimax.backend.repository.FlockRepository;
import com.avimax.backend.repository.MortalityRecordRepository;
import com.avimax.backend.repository.WeightRecordRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;

/**
 * Aplica deltas recibidos del central que afectan registros productivos
 * y estados de alarma locales.
 *
 * Entidades manejadas:
 *  - ALARM: ALARM_ACKNOWLEDGED, ALARM_CLOSED
 *  - MORTALITY_RECORD: MORTALITY_RECORD_CREATED, MORTALITY_RECORD_UPDATED, MORTALITY_RECORD_DELETED_LOGICAL
 *  - WEIGHT_RECORD: WEIGHT_RECORD_CREATED, WEIGHT_RECORD_UPDATED, WEIGHT_RECORD_DELETED_LOGICAL
 *  - CONSUMPTION_RECORD: CONSUMPTION_RECORD_CREATED, CONSUMPTION_RECORD_UPDATED, CONSUMPTION_RECORD_DELETED_LOGICAL
 */
@Service
public class LocalProductiveRecordDeltaService {

    private static final Logger log = LoggerFactory.getLogger(LocalProductiveRecordDeltaService.class);

    private static final Set<AlarmStatus> OPEN_STATUSES      = Set.of(AlarmStatus.ACTIVA, AlarmStatus.RECONOCIDA);
    private static final String SYNC_DELETED                  = "DELETED_LOGICAL";
    private static final String SYNC_SYNCED                   = "SYNCED";
    private static final String FIELD_LOCAL_RECORD_ID         = "localRecordId";
    private static final String FIELD_CENTRAL_RECORD_ID       = "centralRecordId";
    private static final String FIELD_OBSERVATIONS            = "observations";
    private static final String FIELD_AVG_WEIGHT_GRAMS        = "averageWeightGrams";
    private static final String FIELD_SAMPLE_SIZE             = "sampleSize";
    private static final String FIELD_FEED_KG                 = "feedKg";
    private static final String FIELD_GENDER                  = "gender";
    private static final String FIELD_LOCATION                = "location";

    private final AlarmRepository             alarmRepository;
    private final AlarmEventRepository        alarmEventRepository;
    private final MortalityRecordRepository   mortalityRecordRepository;
    private final WeightRecordRepository      weightRecordRepository;
    private final ConsumptionRecordRepository consumptionRecordRepository;
    private final FlockRepository             flockRepository;

    public LocalProductiveRecordDeltaService(AlarmRepository alarmRepository,
                                              AlarmEventRepository alarmEventRepository,
                                              MortalityRecordRepository mortalityRecordRepository,
                                              WeightRecordRepository weightRecordRepository,
                                              ConsumptionRecordRepository consumptionRecordRepository,
                                              FlockRepository flockRepository) {
        this.alarmRepository             = alarmRepository;
        this.alarmEventRepository        = alarmEventRepository;
        this.mortalityRecordRepository   = mortalityRecordRepository;
        this.weightRecordRepository      = weightRecordRepository;
        this.consumptionRecordRepository = consumptionRecordRepository;
        this.flockRepository             = flockRepository;
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
        if ("MORTALITY_RECORD_CREATED".equals(changeType)) {
            return createMortalityRecordFromDelta(data);
        }

        MortalityRecord mortality = findMortalityRecord(data);
        if (mortality == null) return "Registro de mortalidad no encontrado en delta";

        if ("MORTALITY_RECORD_DELETED_LOGICAL".equals(changeType)) {
            mortality.setSyncStatus(SYNC_DELETED);
            mortalityRecordRepository.save(mortality);
            log.info("[ProductiveDelta] Mortalidad id={} marcada como eliminada desde central", mortality.getId());
            return null;
        }

        if (data.hasNonNull("quantity"))               mortality.setTotalCount(data.get("quantity").asInt());
        if (data.hasNonNull(FIELD_OBSERVATIONS)) mortality.setObservations(data.get(FIELD_OBSERVATIONS).asText());
        mortality.setSyncStatus(SYNC_SYNCED);
        mortalityRecordRepository.save(mortality);
        log.info("[ProductiveDelta] Mortalidad id={} actualizada desde central", mortality.getId());
        return null;
    }

    // ── Peso ──────────────────────────────────────────────────────────────────

    @Transactional
    public String applyWeightRecordDelta(String changeType, JsonNode data) {
        if ("WEIGHT_RECORD_CREATED".equals(changeType)) {
            return createWeightRecordFromDelta(data);
        }

        WeightRecord weight = findWeightRecord(data);
        if (weight == null) return "Registro de peso no encontrado en delta";

        if ("WEIGHT_RECORD_DELETED_LOGICAL".equals(changeType)) {
            weight.setSyncStatus(SYNC_DELETED);
            weightRecordRepository.save(weight);
            log.info("[ProductiveDelta] Peso id={} marcado como eliminado desde central", weight.getId());
            return null;
        }

        if (data.hasNonNull(FIELD_AVG_WEIGHT_GRAMS)) weight.setAverageWeight(data.get(FIELD_AVG_WEIGHT_GRAMS).asDouble());
        if (data.hasNonNull(FIELD_SAMPLE_SIZE))      weight.setSampledBirdsCount(data.get(FIELD_SAMPLE_SIZE).asInt());
        weight.setSyncStatus(SYNC_SYNCED);
        weightRecordRepository.save(weight);
        log.info("[ProductiveDelta] Peso id={} actualizado desde central", weight.getId());
        return null;
    }

    // ── Consumo ───────────────────────────────────────────────────────────────

    @Transactional
    public String applyConsumptionRecordDelta(String changeType, JsonNode data) {
        if ("CONSUMPTION_RECORD_CREATED".equals(changeType)) {
            return createConsumptionRecordFromDelta(data);
        }

        ConsumptionRecord consumption = findConsumptionRecord(data);
        if (consumption == null) return "Registro de consumo no encontrado en delta";

        if ("CONSUMPTION_RECORD_DELETED_LOGICAL".equals(changeType)) {
            consumption.setSyncStatus(SYNC_DELETED);
            consumptionRecordRepository.save(consumption);
            log.info("[ProductiveDelta] Consumo id={} marcado como eliminado desde central", consumption.getId());
            return null;
        }

        if (data.hasNonNull(FIELD_FEED_KG)) consumption.setTotalConsumptionKg(data.get(FIELD_FEED_KG).asDouble());
        consumption.setSyncStatus(SYNC_SYNCED);
        consumptionRecordRepository.save(consumption);
        log.info("[ProductiveDelta] Consumo id={} actualizado desde central", consumption.getId());
        return null;
    }

    // ── Creación de registros desde central ───────────────────────────────────

    private String createWeightRecordFromDelta(JsonNode data) {
        Flock flock = flockRepository.findFirstByStatus(FlockStatus.ACTIVE).orElse(null);
        if (flock == null) return "Sin parvada activa en Raspberry para registrar peso desde central";

        Long centralRecordId = data.hasNonNull(FIELD_CENTRAL_RECORD_ID) ? data.get(FIELD_CENTRAL_RECORD_ID).asLong() : null;
        if (centralRecordId != null && weightRecordRepository.findByCentralRecordId(centralRecordId).isPresent()) {
            log.info("[ProductiveDelta] Peso centralRecordId={} ya existe localmente — omitido", centralRecordId);
            return null;
        }

        Double avgWeight   = data.hasNonNull(FIELD_AVG_WEIGHT_GRAMS) ? data.get(FIELD_AVG_WEIGHT_GRAMS).asDouble() : null;
        Integer sampleSize = data.hasNonNull(FIELD_SAMPLE_SIZE)      ? data.get(FIELD_SAMPLE_SIZE).asInt()      : null;
        if (avgWeight == null || sampleSize == null) return FIELD_AVG_WEIGHT_GRAMS + " y " + FIELD_SAMPLE_SIZE + " requeridos para WEIGHT_RECORD_CREATED";

        int ageDays = data.hasNonNull("ageDays") ? data.get("ageDays").asInt() : 0;
        LocalDate recordDate = parseDate(data, "recordDate");

        WeightRecord.Gender gender = WeightRecord.Gender.MALE;
        if (data.hasNonNull(FIELD_GENDER)) {
            try { gender = WeightRecord.Gender.valueOf(data.get(FIELD_GENDER).asText().toUpperCase()); }
            catch (IllegalArgumentException ignored) { log.warn("[ProductiveDelta] Género desconocido '{}', usando MALE", data.get(FIELD_GENDER).asText()); }
        }
        WeightRecord.WeightLocation location = WeightRecord.WeightLocation.PANEL;
        if (data.hasNonNull(FIELD_LOCATION)) {
            try { location = WeightRecord.WeightLocation.valueOf(data.get(FIELD_LOCATION).asText().toUpperCase()); }
            catch (IllegalArgumentException ignored) { log.warn("[ProductiveDelta] Ubicación desconocida '{}', usando PANEL", data.get(FIELD_LOCATION).asText()); }
        }

        WeightRecord weight = new WeightRecord(flock, sampleSize, avgWeight, ageDays, recordDate, gender, location);
        weight.setCentralRecordId(centralRecordId);
        weight.setSyncStatus(SYNC_SYNCED);
        weightRecordRepository.save(weight);
        log.info("[ProductiveDelta] Peso creado localmente desde central centralRecordId={}", centralRecordId);
        return null;
    }

    private String createMortalityRecordFromDelta(JsonNode data) {
        Flock flock = flockRepository.findFirstByStatus(FlockStatus.ACTIVE).orElse(null);
        if (flock == null) return "Sin parvada activa en Raspberry para registrar mortalidad desde central";

        Long centralRecordId = data.hasNonNull(FIELD_CENTRAL_RECORD_ID) ? data.get(FIELD_CENTRAL_RECORD_ID).asLong() : null;
        if (centralRecordId != null && mortalityRecordRepository.findByCentralRecordId(centralRecordId).isPresent()) {
            log.info("[ProductiveDelta] Mortalidad centralRecordId={} ya existe localmente — omitido", centralRecordId);
            return null;
        }

        int maleCount   = data.hasNonNull("maleCount")   ? data.get("maleCount").asInt()   : 0;
        int femaleCount = data.hasNonNull("femaleCount")  ? data.get("femaleCount").asInt()  : 0;
        String observations = data.hasNonNull(FIELD_OBSERVATIONS) ? data.get(FIELD_OBSERVATIONS).asText() : null;

        MortalityRecord mortality = new MortalityRecord(flock, maleCount, femaleCount, observations);
        mortality.setCentralRecordId(centralRecordId);
        mortality.setSyncStatus(SYNC_SYNCED);
        mortalityRecordRepository.save(mortality);
        log.info("[ProductiveDelta] Mortalidad creada localmente desde central centralRecordId={}", centralRecordId);
        return null;
    }

    private String createConsumptionRecordFromDelta(JsonNode data) {
        Flock flock = flockRepository.findFirstByStatus(FlockStatus.ACTIVE).orElse(null);
        if (flock == null) return "Sin parvada activa en Raspberry para registrar consumo desde central";

        Long centralRecordId = data.hasNonNull(FIELD_CENTRAL_RECORD_ID) ? data.get(FIELD_CENTRAL_RECORD_ID).asLong() : null;
        if (centralRecordId != null && consumptionRecordRepository.findByCentralRecordId(centralRecordId).isPresent()) {
            log.info("[ProductiveDelta] Consumo centralRecordId={} ya existe localmente — omitido", centralRecordId);
            return null;
        }

        double feedKg      = data.hasNonNull(FIELD_FEED_KG) ? data.get(FIELD_FEED_KG).asDouble() : 0.0;
        LocalDate recordDate = parseDate(data, "recordDate");
        int ageDays = (int) ChronoUnit.DAYS.between(flock.getFlockDate(), recordDate);
        int birdsCount = flock.getTotalBirds() != null && flock.getTotalBirds() > 0 ? flock.getTotalBirds() : 1;
        double perBird = feedKg / birdsCount;

        ConsumptionRecord consumption = new ConsumptionRecord(flock, ageDays, recordDate, feedKg, birdsCount, perBird);
        consumption.setCentralRecordId(centralRecordId);
        consumption.setSyncStatus(SYNC_SYNCED);
        consumptionRecordRepository.save(consumption);
        log.info("[ProductiveDelta] Consumo creado localmente desde central centralRecordId={}", centralRecordId);
        return null;
    }

    private static LocalDate parseDate(JsonNode data, String field) {
        if (data.hasNonNull(field)) {
            try { return LocalDate.parse(data.get(field).asText()); } catch (Exception ignored) { }
        }
        return LocalDate.now();
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

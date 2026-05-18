package com.avimax.backend.service;

import com.avimax.backend.dto.AlarmEventResponse;
import com.avimax.backend.dto.AlarmResponse;
import com.avimax.backend.dto.AlarmRuleResponse;
import com.avimax.backend.dto.CreateAlarmRuleRequest;
import com.avimax.backend.dto.ToggleAlarmRuleRequest;
import com.avimax.backend.dto.UpdateAlarmRuleRequest;
import com.avimax.backend.entity.Alarm;
import com.avimax.backend.entity.AlarmCondition;
import com.avimax.backend.entity.AlarmEvent;
import com.avimax.backend.entity.AlarmEventType;
import com.avimax.backend.entity.AlarmRule;
import com.avimax.backend.entity.AlarmSeverity;
import com.avimax.backend.entity.AlarmStatus;
import com.avimax.backend.entity.AlarmVariable;
import com.avimax.backend.repository.AlarmEventRepository;
import com.avimax.backend.repository.AlarmRepository;
import com.avimax.backend.repository.AlarmRuleRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AlarmService {

    private static final Set<AlarmStatus> OPEN_STATUSES = Set.of(AlarmStatus.ACTIVA, AlarmStatus.RECONOCIDA);

    private final AlarmRuleRepository alarmRuleRepository;
    private final AlarmRepository alarmRepository;
    private final AlarmEventRepository alarmEventRepository;
    private final AlarmMqttPublisherService alarmMqttPublisherService;

    public AlarmService(AlarmRuleRepository alarmRuleRepository,
                        AlarmRepository alarmRepository,
                        AlarmEventRepository alarmEventRepository,
                        AlarmMqttPublisherService alarmMqttPublisherService) {
        this.alarmRuleRepository = alarmRuleRepository;
        this.alarmRepository = alarmRepository;
        this.alarmEventRepository = alarmEventRepository;
        this.alarmMqttPublisherService = alarmMqttPublisherService;
    }

    @Transactional
    public AlarmRuleResponse createRule(CreateAlarmRuleRequest request) {
        AlarmRule rule = new AlarmRule(
                request.nombre(),
                request.variable(),
                request.condicion(),
                request.umbral(),
                request.unidad(),
                request.tiempoMinimoSegundos(),
                request.severidad(),
                request.mensaje(),
                request.activa()
        );
        return AlarmRuleResponse.fromEntity(alarmRuleRepository.save(rule));
    }

    @Transactional(readOnly = true)
    public List<AlarmRuleResponse> listRules() {
        return alarmRuleRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(AlarmRuleResponse::fromEntity)
                .toList();
    }

    @Transactional
    public AlarmRuleResponse updateRule(Long ruleId, UpdateAlarmRuleRequest request) {
        AlarmRule rule = findRule(ruleId);
        rule.update(
                request.nombre(),
                request.variable(),
                request.condicion(),
                request.umbral(),
                request.unidad(),
                request.tiempoMinimoSegundos(),
                request.severidad(),
                request.mensaje()
        );
        return AlarmRuleResponse.fromEntity(alarmRuleRepository.save(rule));
    }

    @Transactional
    public AlarmRuleResponse setRuleActive(Long ruleId, ToggleAlarmRuleRequest request) {
        AlarmRule rule = findRule(ruleId);
        rule.setActive(request.activa());
        return AlarmRuleResponse.fromEntity(alarmRuleRepository.save(rule));
    }

    @Transactional(readOnly = true)
    public List<AlarmResponse> listActiveAlarms() {
        return alarmRepository.findByStatusInOrderByActivatedAtDesc(OPEN_STATUSES)
                .stream()
                .map(AlarmResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AlarmResponse> alarmHistory() {
        return alarmRepository.findAllByOrderByActivatedAtDesc()
                .stream()
                .map(AlarmResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AlarmEventResponse> alarmEvents(Long alarmId) {
        return alarmEventRepository.findByAlarmIdOrderByEventAtDesc(alarmId)
                .stream()
                .map(AlarmEventResponse::fromEntity)
                .toList();
    }

    @Transactional
    public AlarmResponse acknowledgeAlarm(Long alarmId) {
        Alarm alarm = findAlarm(alarmId);
        if (alarm.getStatus() != AlarmStatus.ACTIVA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede reconocer una alarma en estado ACTIVA");
        }

        OffsetDateTime now = OffsetDateTime.now();
        AlarmStatus previous = alarm.getStatus();
        alarm.recognize(now);
        alarmRepository.save(alarm);

        registerEvent(alarm, AlarmEventType.ALARMA_RECONOCIDA, previous, alarm.getStatus(), "Alarma reconocida manualmente", now);
        alarmMqttPublisherService.publishEvent(AlarmEventType.ALARMA_RECONOCIDA, alarm, null, now);
        return AlarmResponse.fromEntity(alarm);
    }

    @Transactional
    public AlarmResponse closeAlarm(Long alarmId) {
        Alarm alarm = findAlarm(alarmId);
        if (alarm.getStatus() == AlarmStatus.CERRADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La alarma ya se encuentra cerrada");
        }
        if (alarm.getStatus() == AlarmStatus.ACTIVA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede cerrar una alarma ACTIVA sin reconocer o resolver");
        }

        OffsetDateTime now = OffsetDateTime.now();
        AlarmStatus previous = alarm.getStatus();
        alarm.close(now);
        alarmRepository.save(alarm);

        registerEvent(alarm, AlarmEventType.ALARMA_CERRADA, previous, alarm.getStatus(), "Alarma cerrada manualmente", now);
        alarmMqttPublisherService.publishEvent(AlarmEventType.ALARMA_CERRADA, alarm, null, now);
        return AlarmResponse.fromEntity(alarm);
    }

    @Transactional
    public Optional<Alarm> createActivationIfNeeded(AlarmRule rule, Double detectedValue, OffsetDateTime eventAt) {
        Optional<Alarm> open = alarmRepository.findTopByRuleIdAndStatusInOrderByActivatedAtDesc(rule.getId(), OPEN_STATUSES);
        if (open.isPresent()) {
            return Optional.empty();
        }

        Alarm alarm = new Alarm(
                rule,
                rule.getName(),
                rule.getVariable(),
                detectedValue,
                rule.getThreshold(),
                rule.getUnit(),
                rule.getConditionType(),
                rule.getSeverity(),
                rule.getMessage(),
                eventAt
        );

        Alarm saved = alarmRepository.save(alarm);
        registerEvent(saved, AlarmEventType.ALARMA_ACTIVADA, null, AlarmStatus.ACTIVA, "Condición de alarma cumplida durante el tiempo mínimo configurado", eventAt);
        alarmMqttPublisherService.publishEvent(AlarmEventType.ALARMA_ACTIVADA, saved, null, eventAt);
        return Optional.of(saved);
    }

    @Transactional
    public Optional<Alarm> resolveOpenAlarm(Long ruleId, String message, OffsetDateTime eventAt) {
        Optional<Alarm> open = alarmRepository.findTopByRuleIdAndStatusInOrderByActivatedAtDesc(ruleId, OPEN_STATUSES);
        if (open.isEmpty()) {
            return Optional.empty();
        }

        Alarm alarm = open.get();
        AlarmStatus previous = alarm.getStatus();
        alarm.resolve(eventAt);
        alarmRepository.save(alarm);

        registerEvent(alarm, AlarmEventType.ALARMA_RESUELTA, previous, AlarmStatus.RESUELTA, message, eventAt);
        alarmMqttPublisherService.publishEvent(AlarmEventType.ALARMA_RESUELTA, alarm, message, eventAt);
        return Optional.of(alarm);
    }

    @Transactional(readOnly = true)
    public List<AlarmRule> getActiveRules() {
        return alarmRuleRepository.findByActiveTrueOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Optional<Alarm> getOpenAlarmForRule(Long ruleId) {
        return alarmRepository.findTopByRuleIdAndStatusInOrderByActivatedAtDesc(ruleId, OPEN_STATUSES);
    }

    private void registerEvent(Alarm alarm,
                               AlarmEventType eventType,
                               AlarmStatus previous,
                               AlarmStatus next,
                               String description,
                               OffsetDateTime eventAt) {
        AlarmEvent event = new AlarmEvent(alarm, eventType, previous, next, description, eventAt);
        alarmEventRepository.save(event);
    }

    private AlarmRule findRule(Long ruleId) {
        return alarmRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Regla de alarma no encontrada"));
    }

    private Alarm findAlarm(Long alarmId) {
        return alarmRepository.findById(alarmId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alarma no encontrada"));
    }
}

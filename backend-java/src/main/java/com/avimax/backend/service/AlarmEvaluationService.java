package com.avimax.backend.service;

import com.avimax.backend.entity.AlarmCondition;
import com.avimax.backend.entity.AlarmRule;
import com.avimax.backend.entity.AlarmRuleState;
import com.avimax.backend.entity.AlarmVariable;
import com.avimax.backend.entity.SensorReading;
import com.avimax.backend.repository.AlarmRuleStateRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlarmEvaluationService {

    private final AlarmService alarmService;
    private final AlarmRuleStateRepository alarmRuleStateRepository;

    public AlarmEvaluationService(AlarmService alarmService, AlarmRuleStateRepository alarmRuleStateRepository) {
        this.alarmService = alarmService;
        this.alarmRuleStateRepository = alarmRuleStateRepository;
    }

    @Transactional
    public void evaluate(SensorReading reading) {
        List<AlarmRule> activeRules = alarmService.getActiveRules();
        OffsetDateTime readingAt = reading.getRecordedAt() != null ? reading.getRecordedAt() : OffsetDateTime.now();

        for (AlarmRule rule : activeRules) {
            evaluateRuleForReading(rule, reading, readingAt);
        }
    }

    private void evaluateRuleForReading(AlarmRule rule, SensorReading reading, OffsetDateTime readingAt) {
        Double value = valueFor(rule.getVariable(), reading);
        if (value == null) {
            return;
        }

        boolean conditionMet = compare(value, rule.getConditionType(), rule.getThreshold());
        AlarmRuleState state = findOrCreateState(rule);

        if (conditionMet) {
            handleMetCondition(rule, state, value, readingAt);
            return;
        }

        handleClearedCondition(rule, state, value, readingAt);
    }

    private AlarmRuleState findOrCreateState(AlarmRule rule) {
        return alarmRuleStateRepository.findByRuleId(rule.getId())
                .orElseGet(() -> new AlarmRuleState(rule));
    }

    private void handleMetCondition(AlarmRule rule, AlarmRuleState state, Double value, OffsetDateTime readingAt) {
        state.markConditionMet(readingAt, value, readingAt);
        alarmRuleStateRepository.save(state);

        if (alarmService.getOpenAlarmForRule(rule.getId()).isPresent()) {
            return;
        }

        OffsetDateTime metSince = state.getMetSince() != null ? state.getMetSince() : readingAt;
        long elapsed = secondsElapsed(metSince, readingAt);
        if (elapsed >= rule.getMinimumDurationSeconds()) {
            alarmService.createActivationIfNeeded(rule, value, readingAt);
        }
    }

    private void handleClearedCondition(AlarmRule rule, AlarmRuleState state, Double value, OffsetDateTime readingAt) {
        state.markConditionCleared(value, readingAt);
        alarmRuleStateRepository.save(state);
        alarmService.resolveOpenAlarm(rule.getId(), "La condición de alarma volvió a un rango normal.", readingAt);
    }

    private long secondsElapsed(OffsetDateTime since, OffsetDateTime now) {
        if (now.isBefore(since)) {
            return 0;
        }
        return Duration.between(since, now).getSeconds();
    }

    private Double valueFor(AlarmVariable variable, SensorReading reading) {
        return switch (variable) {
            case TEMPERATURA -> reading.getTemperatureC();
            case HUMEDAD -> reading.getHumidityPercent();
            case AMONIACO -> reading.getNh3Ppm();
        };
    }

    private boolean compare(Double value, AlarmCondition condition, Double threshold) {
        return switch (condition) {
            case MAYOR -> value > threshold;
            case MAYOR_IGUAL -> value >= threshold;
            case MENOR -> value < threshold;
            case MENOR_IGUAL -> value <= threshold;
            case IGUAL -> Double.compare(value, threshold) == 0;
        };
    }
}

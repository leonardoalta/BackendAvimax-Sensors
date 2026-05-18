package com.avimax.backend.controller;

import com.avimax.backend.dto.AlarmEventResponse;
import com.avimax.backend.dto.AlarmResponse;
import com.avimax.backend.dto.AlarmRuleResponse;
import com.avimax.backend.dto.CreateAlarmRuleRequest;
import com.avimax.backend.dto.ToggleAlarmRuleRequest;
import com.avimax.backend.dto.UpdateAlarmRuleRequest;
import com.avimax.backend.service.AlarmService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alarms")
public class AlarmController {

    private final AlarmService alarmService;

    public AlarmController(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    @PostMapping("/rules")
    public ResponseEntity<AlarmRuleResponse> createRule(@Valid @RequestBody CreateAlarmRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(alarmService.createRule(request));
    }

    @GetMapping("/rules")
    public List<AlarmRuleResponse> listRules() {
        return alarmService.listRules();
    }

    @PutMapping("/rules/{ruleId}")
    public AlarmRuleResponse updateRule(
            @PathVariable Long ruleId,
            @Valid @RequestBody UpdateAlarmRuleRequest request
    ) {
        return alarmService.updateRule(ruleId, request);
    }

    @PatchMapping("/rules/{ruleId}/active")
    public AlarmRuleResponse setRuleActive(
            @PathVariable Long ruleId,
            @Valid @RequestBody ToggleAlarmRuleRequest request
    ) {
        return alarmService.setRuleActive(ruleId, request);
    }

    @GetMapping("/active")
    public List<AlarmResponse> activeAlarms() {
        return alarmService.listActiveAlarms();
    }

    @GetMapping("/history")
    public List<AlarmResponse> alarmHistory() {
        return alarmService.alarmHistory();
    }

    @GetMapping("/{alarmId}/events")
    public List<AlarmEventResponse> alarmEvents(@PathVariable Long alarmId) {
        return alarmService.alarmEvents(alarmId);
    }

    @PostMapping("/{alarmId}/acknowledge")
    public AlarmResponse acknowledgeAlarm(@PathVariable Long alarmId) {
        return alarmService.acknowledgeAlarm(alarmId);
    }

    @PostMapping("/{alarmId}/close")
    public AlarmResponse closeAlarm(@PathVariable Long alarmId) {
        return alarmService.closeAlarm(alarmId);
    }
}

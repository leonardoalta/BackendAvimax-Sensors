package com.avimax.backend.controller;

import com.avimax.backend.dto.ActuatorSignalResponse;
import com.avimax.backend.dto.ControlEvaluationResponse;
import com.avimax.backend.entity.ActuatorControlCommand;
import com.avimax.backend.entity.SensorReading;
import com.avimax.backend.service.ActuatorControlService;
import com.avimax.backend.service.SensorReadingService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/control")
public class ControlController {

    private final SensorReadingService sensorReadingService;
    private final ActuatorControlService actuatorControlService;

    public ControlController(SensorReadingService sensorReadingService, ActuatorControlService actuatorControlService) {
        this.sensorReadingService = sensorReadingService;
        this.actuatorControlService = actuatorControlService;
    }

    @PostMapping("/evaluate/latest")
    public ResponseEntity<ControlEvaluationResponse> evaluateLatest() {
        SensorReading reading = sensorReadingService.getLatestReading()
                .orElseThrow(() -> new IllegalStateException("No hay lecturas disponibles"));
        return ResponseEntity.ok(actuatorControlService.evaluateAndQueue(reading));
    }

    @GetMapping("/commands/pending")
    public List<ActuatorSignalResponse> pendingCommands() {
        return actuatorControlService.getPendingCommands().stream()
                .map(command -> ActuatorSignalResponse.of(
                        command.getId(),
                        command.getActuatorType(),
                        command.getActuatorId(),
                        command.getActuatorName(),
                        command.getCommand(),
                        command.getWorkDurationSeconds(),
                        command.getReason(),
                        command.getCreatedAt()
                ))
                .toList();
    }

    @PostMapping("/commands/{commandId}/dispatch")
    public ActuatorSignalResponse dispatch(@PathVariable Long commandId) {
        ActuatorControlCommand command = actuatorControlService.markDispatched(commandId);
        return ActuatorSignalResponse.of(
                command.getId(),
                command.getActuatorType(),
                command.getActuatorId(),
                command.getActuatorName(),
                command.getCommand(),
                command.getWorkDurationSeconds(),
                command.getReason(),
                command.getCreatedAt()
        );
    }
}

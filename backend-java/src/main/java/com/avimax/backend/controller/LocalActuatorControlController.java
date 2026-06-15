package com.avimax.backend.controller;

import com.avimax.backend.dto.LocalManualActuatorCommandRequest;
import com.avimax.backend.dto.LocalManualActuatorCommandResponse;
import com.avimax.backend.service.ActuatorControlService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/local/actuadores")
public class LocalActuatorControlController {

    private final ActuatorControlService actuatorControlService;

    public LocalActuatorControlController(ActuatorControlService actuatorControlService) {
        this.actuatorControlService = actuatorControlService;
    }

    @PostMapping("/manual")
    public ResponseEntity<LocalManualActuatorCommandResponse> manual(
            @Valid @RequestBody LocalManualActuatorCommandRequest request) {
        return ResponseEntity.ok(actuatorControlService.applyLocalManualCommand(request));
    }
}

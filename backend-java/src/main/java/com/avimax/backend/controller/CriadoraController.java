package com.avimax.backend.controller;

import com.avimax.backend.dto.ConfigureCriadoraProgrammingRequest;
import com.avimax.backend.dto.CriadoraItemResponse;
import com.avimax.backend.dto.CreateCriadoraRequest;
import com.avimax.backend.service.CriadoraService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/criadoras")
public class CriadoraController {

    private final CriadoraService criadoraService;

    public CriadoraController(CriadoraService criadoraService) {
        this.criadoraService = criadoraService;
    }

    @PostMapping
    public ResponseEntity<CriadoraItemResponse> create(@Valid @RequestBody CreateCriadoraRequest request) {
        var created = criadoraService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CriadoraItemResponse.from(created, null));
    }

    @GetMapping
    public List<CriadoraItemResponse> list() {
        return criadoraService.listWithProgramming();
    }

    @PutMapping("/{criadoraId}/programming")
    public CriadoraItemResponse updateProgramming(
            @PathVariable Long criadoraId,
            @Valid @RequestBody ConfigureCriadoraProgrammingRequest request
    ) {
        var programming = criadoraService.configureProgramming(criadoraId, request);
        return CriadoraItemResponse.from(programming.getCriadora(), programming);
    }

    @GetMapping("/{criadoraId}/history")
    public List<com.avimax.backend.dto.CriadoraHistoryResponse> history(
            @PathVariable Long criadoraId,
            @RequestParam(required = false) Integer limit
    ) {
        return criadoraService.getHistory(criadoraId, limit).stream()
            .map(h -> com.avimax.backend.dto.CriadoraHistoryResponse.of(h.getId(), h.getCriadora().getId(), h.getActuatorName(), h.getActuatorType(), h.getTemperatureOn(), h.getTemperatureOff(), h.getRecordedAt()))
                .toList();
    }

    @GetMapping("/history")
    public List<com.avimax.backend.dto.CriadoraHistoryResponse> historyAll(@RequestParam(required = false) Integer limit) {
        return criadoraService.getAllHistory(limit).stream()
            .map(h -> com.avimax.backend.dto.CriadoraHistoryResponse.of(h.getId(), h.getCriadora().getId(), h.getActuatorName(), h.getActuatorType(), h.getTemperatureOn(), h.getTemperatureOff(), h.getRecordedAt()))
                .toList();
    }
}

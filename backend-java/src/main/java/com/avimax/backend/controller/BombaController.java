package com.avimax.backend.controller;

import com.avimax.backend.dto.ApiResponse;
import com.avimax.backend.dto.BombaItemResponse;
import com.avimax.backend.dto.ConfigureBombaProgrammingRequest;
import com.avimax.backend.dto.CreateBombaRequest;
import com.avimax.backend.service.BombaService;
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
@RequestMapping("/api/bombas")
public class BombaController {

    private final BombaService bombaService;

    public BombaController(BombaService bombaService) {
        this.bombaService = bombaService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BombaItemResponse>> create(@Valid @RequestBody CreateBombaRequest request) {
        var created = bombaService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(BombaItemResponse.from(created, null)));
    }

    @GetMapping
    public ApiResponse<List<BombaItemResponse>> list() {
        return ApiResponse.success(bombaService.listWithProgramming());
    }

    @PutMapping("/{bombaId}/programming")
    public ApiResponse<BombaItemResponse> updateProgramming(
            @PathVariable Long bombaId,
            @Valid @RequestBody ConfigureBombaProgrammingRequest request
    ) {
        var programming = bombaService.configureProgramming(bombaId, request);
        return ApiResponse.success(BombaItemResponse.from(programming.getBomba(), programming));
    }

    @GetMapping("/{bombaId}/history")
    public List<com.avimax.backend.dto.BombaHistoryResponse> history(
            @PathVariable Long bombaId,
            @RequestParam(required = false) Integer limit
    ) {
        return bombaService.getHistory(bombaId, limit).stream()
            .map(h -> com.avimax.backend.dto.BombaHistoryResponse.of(h.getId(), h.getBomba().getId(), h.getActuatorName(), h.getActuatorType(), h.getTemperatureOn(), h.getTemperatureOff(), h.getWorkDurationSeconds(), h.getRecordedAt()))
                .toList();
    }

    @GetMapping("/history")
    public List<com.avimax.backend.dto.BombaHistoryResponse> historyAll(@RequestParam(required = false) Integer limit) {
        return bombaService.getAllHistory(limit).stream()
            .map(h -> com.avimax.backend.dto.BombaHistoryResponse.of(h.getId(), h.getBomba().getId(), h.getActuatorName(), h.getActuatorType(), h.getTemperatureOn(), h.getTemperatureOff(), h.getWorkDurationSeconds(), h.getRecordedAt()))
                .toList();
    }
}

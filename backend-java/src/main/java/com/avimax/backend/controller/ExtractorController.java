package com.avimax.backend.controller;

import com.avimax.backend.dto.ApiResponse;
import com.avimax.backend.dto.ConfigureExtractorProgrammingRequest;
import com.avimax.backend.dto.CreateExtractorRequest;
import com.avimax.backend.dto.ExtractorItemResponse;
import com.avimax.backend.service.ExtractorService;
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
@RequestMapping("/api/extractors")
public class ExtractorController {

    private final ExtractorService extractorService;

    public ExtractorController(ExtractorService extractorService) {
        this.extractorService = extractorService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ExtractorItemResponse>> create(@Valid @RequestBody CreateExtractorRequest request) {
        var created = extractorService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ExtractorItemResponse.from(created, null)));
    }

    @GetMapping
    public ApiResponse<List<ExtractorItemResponse>> list() {
        return ApiResponse.success(extractorService.listWithProgramming());
    }

    @PutMapping("/{extractorId}/programming")
    public ApiResponse<ExtractorItemResponse> updateProgramming(
            @PathVariable Long extractorId,
            @Valid @RequestBody ConfigureExtractorProgrammingRequest request
    ) {
        var programming = extractorService.configureProgramming(extractorId, request);
        return ApiResponse.success(ExtractorItemResponse.from(programming.getExtractor(), programming));
    }

    @GetMapping("/{extractorId}/history")
    public List<com.avimax.backend.dto.ExtractorHistoryResponse> history(
            @PathVariable Long extractorId,
            @RequestParam(required = false) Integer limit
    ) {
        return extractorService.getHistory(extractorId, limit).stream()
            .map(h -> com.avimax.backend.dto.ExtractorHistoryResponse.of(h.getId(), h.getExtractor().getId(), h.getActuatorName(), h.getActuatorType(), h.getTemperatureOn(), h.getTemperatureOff(), h.getRecordedAt()))
                .toList();
    }

    @GetMapping("/history")
    public List<com.avimax.backend.dto.ExtractorHistoryResponse> historyAll(@RequestParam(required = false) Integer limit) {
        return extractorService.getAllHistory(limit).stream()
            .map(h -> com.avimax.backend.dto.ExtractorHistoryResponse.of(h.getId(), h.getExtractor().getId(), h.getActuatorName(), h.getActuatorType(), h.getTemperatureOn(), h.getTemperatureOff(), h.getRecordedAt()))
                .toList();
    }
}

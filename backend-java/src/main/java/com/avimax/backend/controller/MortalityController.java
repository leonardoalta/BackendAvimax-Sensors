package com.avimax.backend.controller;

import com.avimax.backend.dto.CreateMortalityRequest;
import com.avimax.backend.dto.MortalityResponse;
import com.avimax.backend.entity.MortalityRecord;
import com.avimax.backend.service.MortalityService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mortalidad")
public class MortalityController {

    private final MortalityService mortalityService;

    public MortalityController(MortalityService mortalityService) {
        this.mortalityService = mortalityService;
    }

    @PostMapping
    public ResponseEntity<MortalityResponse> create(@Valid @RequestBody CreateMortalityRequest request) {
        MortalityRecord saved = mortalityService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(MortalityResponse.of(
                saved.getId(), saved.getRecordDate(), saved.getAgeDays(), saved.getMaleCount(), saved.getFemaleCount(), saved.getTotalCount(), saved.getObservations(), saved.getCreatedAt()
        ));
    }

    @GetMapping
    public List<MortalityResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        List<MortalityRecord> list;
        if (from != null && to != null) {
            list = mortalityService.listBetween(from, to);
        } else {
            list = mortalityService.listAll();
        }

        return list.stream()
                .map(r -> MortalityResponse.of(r.getId(), r.getRecordDate(), r.getAgeDays(), r.getMaleCount(), r.getFemaleCount(), r.getTotalCount(), r.getObservations(), r.getCreatedAt()))
                .toList();
    }
}

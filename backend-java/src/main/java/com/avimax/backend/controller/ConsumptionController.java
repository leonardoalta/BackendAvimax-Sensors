package com.avimax.backend.controller;

import com.avimax.backend.dto.ConsumptionResponse;
import com.avimax.backend.dto.CreateConsumptionRequest;
import com.avimax.backend.entity.ConsumptionRecord;
import com.avimax.backend.service.ConsumptionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/consumo")
public class ConsumptionController {

    private final ConsumptionService consumptionService;

    public ConsumptionController(ConsumptionService consumptionService) {
        this.consumptionService = consumptionService;
    }

    @PostMapping
    public ResponseEntity<ConsumptionResponse> create(@Valid @RequestBody CreateConsumptionRequest request) {
        ConsumptionRecord saved = consumptionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ConsumptionResponse(
                saved.getId(), saved.getFlock().getId(), saved.getAge(), saved.getRecordDate(),
                saved.getTotalConsumptionKg(), saved.getBirdsCountUsed(), saved.getConsumptionPerBirdKg(), saved.getCreatedAt()
        ));
    }

    @GetMapping
    public List<ConsumptionResponse> listAll() {
        return consumptionService.listAll();
    }

    @GetMapping("/flock/{flockId}")
    public List<ConsumptionResponse> listByFlock(@PathVariable Long flockId) {
        return consumptionService.listByFlock(flockId);
    }
}

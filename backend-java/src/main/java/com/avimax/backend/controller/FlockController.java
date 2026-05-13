package com.avimax.backend.controller;

import com.avimax.backend.dto.CreateFlockRequest;
import com.avimax.backend.dto.FlockResponse;
import com.avimax.backend.service.FlockService;
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
@RequestMapping("/api/flocks")
public class FlockController {

    private final FlockService flockService;

    public FlockController(FlockService flockService) {
        this.flockService = flockService;
    }

    @PostMapping
    public ResponseEntity<FlockResponse> create(@Valid @RequestBody CreateFlockRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(FlockResponse.fromEntity(flockService.createActiveFlock(request)));
    }

    @GetMapping
    public List<FlockResponse> list() {
        return flockService.getAllFlocks().stream()
                .map(FlockResponse::fromEntity)
                .toList();
    }

    @GetMapping("/active")
    public ResponseEntity<FlockResponse> active() {
        return flockService.getActiveFlock()
                .map(FlockResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{id}/close")
    public FlockResponse close(@PathVariable Long id) {
        return FlockResponse.fromEntity(flockService.closeFlock(id));
    }
}

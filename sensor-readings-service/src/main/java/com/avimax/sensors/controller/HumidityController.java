package com.avimax.sensors.controller;

import com.avimax.sensors.dto.CreateHumidityReadingRequest;
import com.avimax.sensors.entity.HumidityReading;
import com.avimax.sensors.repository.HumidityReadingRepository;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * HumidityController - Desarrollado por Oscar Hernández
 * Gestiona las lecturas de humedad relativa (%).
 * Validación: rango 0-100%.
 */
@RestController
@RequestMapping("/api/humidity")
public class HumidityController {

    private final HumidityReadingRepository repository;

    public HumidityController(HumidityReadingRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<HumidityReading> create(@Valid @RequestBody CreateHumidityReadingRequest request) {
        HumidityReading reading = new HumidityReading(request.value(), request.location());
        HumidityReading saved = repository.save(reading);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public List<HumidityReading> listAll() {
        return repository.findAll();
    }

    @GetMapping("/location/{location}")
    public List<HumidityReading> getByLocation(@PathVariable String location) {
        return repository.findByLocationOrderByRecordedAtDesc(location);
    }

    @GetMapping("/{id}")
    public ResponseEntity<HumidityReading> getById(@PathVariable Long id) {
        return ResponseEntity.of(repository.findById(id));
    }
}

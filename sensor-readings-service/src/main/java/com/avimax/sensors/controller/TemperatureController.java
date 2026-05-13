package com.avimax.sensors.controller;

import com.avimax.sensors.dto.CreateTemperatureReadingRequest;
import com.avimax.sensors.entity.TemperatureReading;
import com.avimax.sensors.repository.TemperatureReadingRepository;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/temperature")
public class TemperatureController {

    private final TemperatureReadingRepository repository;

    public TemperatureController(TemperatureReadingRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<TemperatureReading> create(@Valid @RequestBody CreateTemperatureReadingRequest request) {
        TemperatureReading reading = new TemperatureReading(request.value(), request.location());
        TemperatureReading saved = repository.save(reading);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public List<TemperatureReading> listAll() {
        return repository.findAll();
    }

    @GetMapping("/location/{location}")
    public List<TemperatureReading> getByLocation(@PathVariable String location) {
        return repository.findByLocationOrderByRecordedAtDesc(location);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TemperatureReading> getById(@PathVariable Long id) {
        return ResponseEntity.of(repository.findById(id));
    }
}

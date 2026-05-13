package com.avimax.sensors.controller;

import com.avimax.sensors.dto.CreateNH3ReadingRequest;
import com.avimax.sensors.entity.NH3Reading;
import com.avimax.sensors.repository.NH3ReadingRepository;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/nh3")
public class NH3Controller {

    private final NH3ReadingRepository repository;

    public NH3Controller(NH3ReadingRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<NH3Reading> create(@Valid @RequestBody CreateNH3ReadingRequest request) {
        NH3Reading reading = new NH3Reading(request.value(), request.location());
        NH3Reading saved = repository.save(reading);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public List<NH3Reading> listAll() {
        return repository.findAll();
    }

    @GetMapping("/location/{location}")
    public List<NH3Reading> getByLocation(@PathVariable String location) {
        return repository.findByLocationOrderByRecordedAtDesc(location);
    }

    @GetMapping("/{id}")
    public ResponseEntity<NH3Reading> getById(@PathVariable Long id) {
        return ResponseEntity.of(repository.findById(id));
    }
}

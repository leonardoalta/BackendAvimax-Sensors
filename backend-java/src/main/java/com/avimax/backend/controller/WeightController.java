package com.avimax.backend.controller;

import com.avimax.backend.dto.CreateWeightRequest;
import com.avimax.backend.dto.WeightResponse;
import com.avimax.backend.entity.WeightRecord.Gender;
import com.avimax.backend.service.WeightService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/peso")
public class WeightController {

    @Autowired
    private WeightService weightService;

    /**
     * Create a new weight record for the active flock
     * POST /api/peso
     */
    @PostMapping
    public ResponseEntity<WeightResponse> createWeightRecord(@RequestBody CreateWeightRequest request) {
        WeightResponse response = weightService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get all weight records
     * GET /api/peso
     */
    @GetMapping
    public ResponseEntity<List<WeightResponse>> getAllWeightRecords() {
        List<WeightResponse> records = weightService.getAllWeightRecords();
        return ResponseEntity.ok(records);
    }

    /**
     * Get weight records by flock
     * GET /api/peso/flock/{flockId}
     */
    @GetMapping("/flock/{flockId}")
    public ResponseEntity<List<WeightResponse>> getWeightRecordsByFlock(@PathVariable Long flockId) {
        List<WeightResponse> records = weightService.getWeightRecordsByFlock(flockId);
        return ResponseEntity.ok(records);
    }

    /**
     * Get weight records by flock and gender
     * GET /api/peso/flock/{flockId}/gender/{gender}
     */
    @GetMapping("/flock/{flockId}/gender/{gender}")
    public ResponseEntity<List<WeightResponse>> getWeightRecordsByFlockAndGender(
            @PathVariable Long flockId,
            @PathVariable String gender) {
        Gender genderEnum = Gender.valueOf(gender.toUpperCase());
        List<WeightResponse> records = weightService.getWeightRecordsByFlockAndGender(flockId, genderEnum);
        return ResponseEntity.ok(records);
    }

    /**
     * Get the latest weight record for a flock and gender
     * GET /api/peso/flock/{flockId}/latest/gender/{gender}
     */
    @GetMapping("/flock/{flockId}/latest/gender/{gender}")
    public ResponseEntity<WeightResponse> getLatestWeightRecord(
            @PathVariable Long flockId,
            @PathVariable String gender) {
        Gender genderEnum = Gender.valueOf(gender.toUpperCase());
        WeightResponse record = weightService.getLatestWeightRecord(flockId, genderEnum);
        return ResponseEntity.ok(record);
    }

    /**
     * Get the latest male weight record for active flock
     * GET /api/peso/latest/male
     */
    @GetMapping("/latest/male")
    public ResponseEntity<WeightResponse> getLatestMaleWeightRecord() {
        WeightResponse record = weightService.getLatestMaleWeightRecord();
        return ResponseEntity.ok(record);
    }

    /**
     * Get the latest female weight record for active flock
     * GET /api/peso/latest/female
     */
    @GetMapping("/latest/female")
    public ResponseEntity<WeightResponse> getLatestFemaleWeightRecord() {
        WeightResponse record = weightService.getLatestFemaleWeightRecord();
        return ResponseEntity.ok(record);
    }

    /**
     * Get weight records by date range for a flock
     * GET /api/peso/flock/{flockId}/range?from=2026-05-01&to=2026-05-10
     */
    @GetMapping("/flock/{flockId}/range")
    public ResponseEntity<List<WeightResponse>> getWeightRecordsByDateRange(
            @PathVariable Long flockId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        List<WeightResponse> records = weightService.getWeightRecordsByDateRange(flockId, from, to);
        return ResponseEntity.ok(records);
    }
}

package com.avimax.backend.service;

import com.avimax.backend.dto.CreateWeightRequest;
import com.avimax.backend.dto.WeightResponse;
import com.avimax.backend.entity.Flock;
import com.avimax.backend.entity.FlockStatus;
import com.avimax.backend.entity.WeightRecord;
import com.avimax.backend.entity.WeightRecord.Gender;
import com.avimax.backend.repository.FlockRepository;
import com.avimax.backend.repository.WeightRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WeightService {

    @Autowired
    private WeightRecordRepository weightRecordRepository;

    @Autowired
    private FlockRepository flockRepository;

    /**
     * Create a new weight record for the active flock
     */
    public WeightResponse create(CreateWeightRequest request) {
        // Get active flock
        Flock activeFlock = flockRepository.findFirstByStatus(FlockStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No hay parvada activa"));

        // Validate inputs
        if (request.getSampledBirdsCount() == null || request.getSampledBirdsCount() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sampledBirdsCount must be greater than 0");
        }
        if (request.getAverageWeight() == null || request.getAverageWeight() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "averageWeight must be greater than 0");
        }
        if (request.getAge() == null || request.getAge() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "age cannot be negative");
        }
        if (request.getRecordDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "recordDate is required");
        }
        if (request.getGender() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "gender is required");
        }
        if (request.getLocation() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "location is required");
        }

        // Create and save weight record
        WeightRecord weightRecord = new WeightRecord(
            activeFlock,
            request.getSampledBirdsCount(),
            request.getAverageWeight(),
            request.getAge(),
            request.getRecordDate(),
            request.getGender(),
            request.getLocation()
        );

        WeightRecord saved = weightRecordRepository.save(weightRecord);
        return convertToResponse(saved);
    }

    /**
     * Get all weight records
     */
    public List<WeightResponse> getAllWeightRecords() {
        return weightRecordRepository.findAllByOrderByRecordDateDesc()
            .stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    /**
     * Get weight records by flock
     */
    public List<WeightResponse> getWeightRecordsByFlock(Long flockId) {
        return weightRecordRepository.findByFlockIdOrderByRecordDateDesc(flockId)
            .stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    /**
     * Get weight records by flock and gender
     */
    public List<WeightResponse> getWeightRecordsByFlockAndGender(Long flockId, Gender gender) {
        return weightRecordRepository.findByFlockIdAndGenderOrderByRecordDateDesc(flockId, gender)
            .stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    /**
     * Get the latest weight record for a flock and gender
     */
    public WeightResponse getLatestWeightRecord(Long flockId, Gender gender) {
        WeightRecord latestRecord = weightRecordRepository
            .findFirstByFlockIdAndGenderOrderByRecordDateDescCreatedAtDesc(flockId, gender)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No weight record found for flock " + flockId + " and gender " + gender));

        return convertToResponse(latestRecord);
    }

    /**
     * Get weight records by date range for a flock
     */
    public List<WeightResponse> getWeightRecordsByDateRange(Long flockId, LocalDate fromDate, LocalDate toDate) {
        return weightRecordRepository.findByFlockIdAndDateRange(flockId, fromDate, toDate)
            .stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    /**
     * Get the latest male weight record for active flock
     */
    public WeightResponse getLatestMaleWeightRecord() {
        Flock activeFlock = flockRepository.findFirstByStatus(FlockStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No hay parvada activa"));

        return getLatestWeightRecord(activeFlock.getId(), Gender.MALE);
    }

    /**
     * Get the latest female weight record for active flock
     */
    public WeightResponse getLatestFemaleWeightRecord() {
        Flock activeFlock = flockRepository.findFirstByStatus(FlockStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No hay parvada activa"));

        return getLatestWeightRecord(activeFlock.getId(), Gender.FEMALE);
    }

    /**
     * Convert WeightRecord to WeightResponse
     */
    private WeightResponse convertToResponse(WeightRecord record) {
        return new WeightResponse(
            record.getId(),
            record.getFlock().getId(),
            record.getSampledBirdsCount(),
            record.getAverageWeight(),
            record.getAge(),
            record.getRecordDate(),
            record.getGender(),
            record.getLocation(),
            record.getCreatedAt()
        );
    }
}

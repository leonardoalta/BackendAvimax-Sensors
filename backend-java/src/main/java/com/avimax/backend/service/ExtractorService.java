package com.avimax.backend.service;

import com.avimax.backend.dto.ConfigureExtractorProgrammingRequest;
import com.avimax.backend.dto.CreateExtractorRequest;
import com.avimax.backend.dto.ExtractorItemResponse;
import com.avimax.backend.entity.Extractor;
import com.avimax.backend.entity.ExtractorProgramming;
import com.avimax.backend.repository.ExtractorProgrammingRepository;
import com.avimax.backend.repository.ExtractorProgrammingHistoryRepository;
import com.avimax.backend.repository.ExtractorRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExtractorService {

    private final ExtractorRepository extractorRepository;
    private final ExtractorProgrammingRepository extractorProgrammingRepository;
    private final ExtractorProgrammingHistoryRepository extractorProgrammingHistoryRepository;

    @Autowired(required = false)
    private LocalSyncMqttPublisherService syncPublisher;

    public ExtractorService(ExtractorRepository extractorRepository, ExtractorProgrammingRepository extractorProgrammingRepository, ExtractorProgrammingHistoryRepository extractorProgrammingHistoryRepository) {
        this.extractorRepository = extractorRepository;
        this.extractorProgrammingRepository = extractorProgrammingRepository;
        this.extractorProgrammingHistoryRepository = extractorProgrammingHistoryRepository;
    }

    @Transactional
    public Extractor create(CreateExtractorRequest request) {
        Extractor extractor = new Extractor(request.name());
        if (request.codeName() != null && !request.codeName().isBlank()) {
            extractor.setCodeName(request.codeName().toUpperCase().strip());
        }
        Extractor saved = extractorRepository.save(extractor);
        if (syncPublisher != null) syncPublisher.publishActuatorUpsert(saved, "ACTUATOR_CREATED");
        return saved;
    }

    @Transactional
    public Extractor setEnabled(Long extractorId, boolean enabled) {
        Extractor extractor = extractorRepository.findById(extractorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Extractor no encontrado"));
        extractor.setEnabled(enabled);
        Extractor saved = extractorRepository.save(extractor);
        if (syncPublisher != null) {
            syncPublisher.publishActuatorUpsert(saved, enabled ? "ACTUATOR_ENABLED" : "ACTUATOR_DISABLED");
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ExtractorItemResponse> listWithProgramming() {
        return extractorRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(extractor -> ExtractorItemResponse.from(
                        extractor,
                        extractorProgrammingRepository.findByExtractorId(extractor.getId()).orElse(null)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<com.avimax.backend.entity.ExtractorProgrammingHistory> getHistory(Long extractorId, Integer limit) {
        var list = extractorProgrammingHistoryRepository.findByExtractorIdOrderByRecordedAtDesc(extractorId);
        if (limit == null || limit <= 0) {
            return list;
        }
        return list.stream().limit(limit).toList();
    }

    @Transactional(readOnly = true)
    public List<com.avimax.backend.entity.ExtractorProgrammingHistory> getAllHistory(Integer limit) {
        var list = extractorProgrammingHistoryRepository.findAll(Sort.by(Sort.Direction.DESC, "recordedAt"));
        if (limit == null || limit <= 0) {
            return list;
        }
        return list.stream().limit(limit).toList();
    }

    @Transactional
    public ExtractorProgramming configureProgramming(Long extractorId, ConfigureExtractorProgrammingRequest request) {
        Extractor extractor = extractorRepository.findById(extractorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Extractor no encontrado"));
        var existingOpt = extractorProgrammingRepository.findByExtractorId(extractorId);
        ExtractorProgramming saved;
        if (existingOpt.isPresent()) {
            var existing = existingOpt.get();
            existing.update(request.temperatureOn(), request.temperatureOff());
            saved = extractorProgrammingRepository.save(existing);
        } else {
            saved = extractorProgrammingRepository.save(new ExtractorProgramming(extractor, request.temperatureOn(), request.temperatureOff()));
        }

        extractorProgrammingHistoryRepository.save(new com.avimax.backend.entity.ExtractorProgrammingHistory(extractor, saved.getTemperatureOn(), saved.getTemperatureOff()));
        if (syncPublisher != null) {
            syncPublisher.publishProgrammingChanged("EXTRACTOR", extractor.getCodeName(),
                    saved.getTemperatureOn(), saved.getTemperatureOff(), null);
        }
        return saved;
    }
}

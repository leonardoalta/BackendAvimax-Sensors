package com.avimax.backend.service;

import com.avimax.backend.dto.CreateMortalityRequest;
import com.avimax.backend.entity.MortalityRecord;
import com.avimax.backend.entity.Flock;
import com.avimax.backend.repository.MortalityRecordRepository;
import com.avimax.backend.repository.FlockRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MortalityService {

    private final MortalityRecordRepository mortalityRecordRepository;
    private final FlockRepository flockRepository;
    private final LocalSyncMqttPublisherService syncPublisher;

    public MortalityService(MortalityRecordRepository mortalityRecordRepository,
                            FlockRepository flockRepository,
                            LocalSyncMqttPublisherService syncPublisher) {
        this.mortalityRecordRepository = mortalityRecordRepository;
        this.flockRepository = flockRepository;
        this.syncPublisher = syncPublisher;
    }

    @Transactional
    public MortalityRecord create(CreateMortalityRequest request) {
        Flock flock = flockRepository.findFirstByStatus(com.avimax.backend.entity.FlockStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No hay parvada activa para asociar el registro de mortalidad"));

        // Prevent duplicate record for same flock and date
        LocalDate today = LocalDate.now();
        Optional<MortalityRecord> exists = mortalityRecordRepository.findByFlockIdAndRecordDate(flock.getId(), today);
        if (exists.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un registro de mortalidad para la parvada activa en la fecha de hoy");
        }

        MortalityRecord record = new MortalityRecord(flock, request.maleCount(), request.femaleCount(), request.observations());
        MortalityRecord saved = mortalityRecordRepository.save(record);

        flock.reduceCounts(request.maleCount(), request.femaleCount());
        flockRepository.save(flock);

        syncPublisher.publishMortalityRecorded(saved, flock.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<MortalityRecord> listAll() {
        Flock flock = flockRepository.findFirstByStatus(com.avimax.backend.entity.FlockStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay parvada activa para consultar la mortalidad"));
        return mortalityRecordRepository.findByFlockIdOrderByRecordDateDesc(flock.getId());
    }

    @Transactional(readOnly = true)
    public List<MortalityRecord> listBetween(java.time.LocalDate from, java.time.LocalDate to) {
        Flock flock = flockRepository.findFirstByStatus(com.avimax.backend.entity.FlockStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay parvada activa para consultar la mortalidad"));
        return mortalityRecordRepository.findByFlockIdAndRecordDateBetweenOrderByRecordDateDesc(flock.getId(), from, to);
    }
}

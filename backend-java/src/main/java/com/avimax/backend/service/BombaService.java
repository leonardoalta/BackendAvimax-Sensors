package com.avimax.backend.service;

import com.avimax.backend.dto.BombaItemResponse;
import com.avimax.backend.dto.ConfigureBombaProgrammingRequest;
import com.avimax.backend.dto.CreateBombaRequest;
import com.avimax.backend.entity.Bomba;
import com.avimax.backend.entity.BombaProgramming;
import com.avimax.backend.repository.BombaProgrammingRepository;
import com.avimax.backend.repository.BombaProgrammingHistoryRepository;
import com.avimax.backend.repository.BombaRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BombaService {

    private final BombaRepository bombaRepository;
    private final BombaProgrammingRepository bombaProgrammingRepository;
    private final BombaProgrammingHistoryRepository bombaProgrammingHistoryRepository;

    public BombaService(BombaRepository bombaRepository, BombaProgrammingRepository bombaProgrammingRepository, BombaProgrammingHistoryRepository bombaProgrammingHistoryRepository) {
        this.bombaRepository = bombaRepository;
        this.bombaProgrammingRepository = bombaProgrammingRepository;
        this.bombaProgrammingHistoryRepository = bombaProgrammingHistoryRepository;
    }

    @Transactional
    public Bomba create(CreateBombaRequest request) {
        return bombaRepository.save(new Bomba(request.name()));
    }

    @Transactional(readOnly = true)
    public List<BombaItemResponse> listWithProgramming() {
        return bombaRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(bomba -> BombaItemResponse.from(
                        bomba,
                        bombaProgrammingRepository.findByBombaId(bomba.getId()).orElse(null)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<com.avimax.backend.entity.BombaProgrammingHistory> getHistory(Long bombaId, Integer limit) {
        var list = bombaProgrammingHistoryRepository.findByBombaIdOrderByRecordedAtDesc(bombaId);
        if (limit == null || limit <= 0) {
            return list;
        }
        return list.stream().limit(limit).toList();
    }

    @Transactional(readOnly = true)
    public List<com.avimax.backend.entity.BombaProgrammingHistory> getAllHistory(Integer limit) {
        var list = bombaProgrammingHistoryRepository.findAll(Sort.by(Sort.Direction.DESC, "recordedAt"));
        if (limit == null || limit <= 0) {
            return list;
        }
        return list.stream().limit(limit).toList();
    }

    @Transactional
    public BombaProgramming configureProgramming(Long bombaId, ConfigureBombaProgrammingRequest request) {
        Bomba bomba = bombaRepository.findById(bombaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bomba no encontrada"));

        var existingOpt = bombaProgrammingRepository.findByBombaId(bombaId);
        BombaProgramming saved;
        if (existingOpt.isPresent()) {
            var existing = existingOpt.get();
            existing.update(request.temperatureOn(), request.temperatureOff(), request.workDurationSeconds());
            saved = bombaProgrammingRepository.save(existing);
        } else {
            saved = bombaProgrammingRepository.save(new BombaProgramming(bomba, request.temperatureOn(), request.temperatureOff(), request.workDurationSeconds()));
        }

        bombaProgrammingHistoryRepository.save(new com.avimax.backend.entity.BombaProgrammingHistory(bomba, saved.getTemperatureOn(), saved.getTemperatureOff(), saved.getWorkDurationSeconds()));
        return saved;
    }
}

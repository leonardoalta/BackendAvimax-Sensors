package com.avimax.backend.service;

import com.avimax.backend.dto.ConfigureCriadoraProgrammingRequest;
import com.avimax.backend.dto.CriadoraItemResponse;
import com.avimax.backend.dto.CreateCriadoraRequest;
import com.avimax.backend.entity.Criadora;
import com.avimax.backend.entity.CriadoraProgramming;
import com.avimax.backend.repository.CriadoraProgrammingRepository;
import com.avimax.backend.repository.CriadoraProgrammingHistoryRepository;
import com.avimax.backend.repository.CriadoraRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CriadoraService {

    private final CriadoraRepository criadoraRepository;
    private final CriadoraProgrammingRepository criadoraProgrammingRepository;
    private final CriadoraProgrammingHistoryRepository criadoraProgrammingHistoryRepository;

    public CriadoraService(CriadoraRepository criadoraRepository, CriadoraProgrammingRepository criadoraProgrammingRepository, CriadoraProgrammingHistoryRepository criadoraProgrammingHistoryRepository) {
        this.criadoraRepository = criadoraRepository;
        this.criadoraProgrammingRepository = criadoraProgrammingRepository;
        this.criadoraProgrammingHistoryRepository = criadoraProgrammingHistoryRepository;
    }

    @Transactional
    public Criadora create(CreateCriadoraRequest request) {
        return criadoraRepository.save(new Criadora(request.name()));
    }

    @Transactional(readOnly = true)
    public List<CriadoraItemResponse> listWithProgramming() {
        return criadoraRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(criadora -> CriadoraItemResponse.from(
                        criadora,
                        criadoraProgrammingRepository.findByCriadoraId(criadora.getId()).orElse(null)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<com.avimax.backend.entity.CriadoraProgrammingHistory> getHistory(Long criadoraId, Integer limit) {
        var list = criadoraProgrammingHistoryRepository.findByCriadoraIdOrderByRecordedAtDesc(criadoraId);
        if (limit == null || limit <= 0) {
            return list;
        }
        return list.stream().limit(limit).toList();
    }

    @Transactional(readOnly = true)
    public List<com.avimax.backend.entity.CriadoraProgrammingHistory> getAllHistory(Integer limit) {
        var list = criadoraProgrammingHistoryRepository.findAll(Sort.by(Sort.Direction.DESC, "recordedAt"));
        if (limit == null || limit <= 0) {
            return list;
        }
        return list.stream().limit(limit).toList();
    }

    @Transactional
    public CriadoraProgramming configureProgramming(Long criadoraId, ConfigureCriadoraProgrammingRequest request) {
        Criadora criadora = criadoraRepository.findById(criadoraId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Criadora no encontrada"));

        var existingOpt = criadoraProgrammingRepository.findByCriadoraId(criadoraId);
        CriadoraProgramming saved;
        if (existingOpt.isPresent()) {
            var existing = existingOpt.get();
            existing.update(request.temperatureOn(), request.temperatureOff());
            saved = criadoraProgrammingRepository.save(existing);
        } else {
            saved = criadoraProgrammingRepository.save(new CriadoraProgramming(criadora, request.temperatureOn(), request.temperatureOff()));
        }

        criadoraProgrammingHistoryRepository.save(new com.avimax.backend.entity.CriadoraProgrammingHistory(criadora, saved.getTemperatureOn(), saved.getTemperatureOff()));
        return saved;
    }
}

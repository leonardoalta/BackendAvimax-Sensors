package com.avimax.backend.service;

import com.avimax.backend.dto.ConsumptionResponse;
import com.avimax.backend.dto.CreateConsumptionRequest;
import com.avimax.backend.entity.ConsumptionRecord;
import com.avimax.backend.entity.Flock;
import com.avimax.backend.entity.FlockStatus;
import com.avimax.backend.repository.ConsumptionRecordRepository;
import com.avimax.backend.repository.FlockRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConsumptionService {

    private final ConsumptionRecordRepository consumptionRecordRepository;
    private final FlockRepository flockRepository;

    public ConsumptionService(ConsumptionRecordRepository consumptionRecordRepository, FlockRepository flockRepository) {
        this.consumptionRecordRepository = consumptionRecordRepository;
        this.flockRepository = flockRepository;
    }

    @Transactional
    public ConsumptionRecord create(CreateConsumptionRequest request) {
        Flock flock = flockRepository.findFirstByStatus(FlockStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay parvada activa para asociar el consumo"));

        if (request.getAge() == null || request.getAge() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La edad debe ser mayor o igual a 0");
        }
        if (request.getRecordDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha es obligatoria");
        }
        if (request.getTotalConsumptionKg() == null || request.getTotalConsumptionKg() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El consumo total debe ser mayor a 0");
        }
        if (flock.getTotalBirds() == null || flock.getTotalBirds() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La parvada no tiene aves válidas para calcular el consumo por pollo");
        }

        int birdsCountUsed = flock.getTotalBirds();
        double consumptionPerBirdKg = request.getTotalConsumptionKg() / birdsCountUsed;

        ConsumptionRecord record = new ConsumptionRecord(
                flock,
                request.getAge(),
                request.getRecordDate(),
                request.getTotalConsumptionKg(),
                birdsCountUsed,
                consumptionPerBirdKg
        );

        return consumptionRecordRepository.save(record);
    }

    @Transactional(readOnly = true)
    public List<ConsumptionResponse> listAll() {
        return consumptionRecordRepository.findAllByOrderByRecordDateDesc()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ConsumptionResponse> listByFlock(Long flockId) {
        return consumptionRecordRepository.findByFlockIdOrderByRecordDateDesc(flockId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private ConsumptionResponse toResponse(ConsumptionRecord record) {
        return new ConsumptionResponse(
                record.getId(),
                record.getFlock().getId(),
                record.getAge(),
                record.getRecordDate(),
                record.getTotalConsumptionKg(),
                record.getBirdsCountUsed(),
                record.getConsumptionPerBirdKg(),
                record.getCreatedAt()
        );
    }
}

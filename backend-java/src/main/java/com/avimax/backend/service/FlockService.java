package com.avimax.backend.service;

import com.avimax.backend.dto.CreateFlockRequest;
import com.avimax.backend.entity.Flock;
import com.avimax.backend.entity.FlockStatus;
import com.avimax.backend.repository.FlockRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FlockService {

    private final FlockRepository flockRepository;

    public FlockService(FlockRepository flockRepository) {
        this.flockRepository = flockRepository;
    }

    @Transactional
    public Flock createActiveFlock(CreateFlockRequest request) {
        if (flockRepository.existsByStatus(FlockStatus.ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una parvada activa. Debes cerrarla antes de crear otra.");
        }

        if (!request.totalBirds().equals(request.maleCount() + request.femaleCount())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad total debe ser igual a machos + hembras");
        }

        Flock flock = new Flock(
                request.name(),
                request.totalBirds(),
                request.maleCount(),
                request.femaleCount(),
                request.flockDate(),
                request.birdLot(),
                request.notes()
        );
        return flockRepository.save(flock);
    }

    @Transactional(readOnly = true)
    public Optional<Flock> getActiveFlock() {
        return flockRepository.findFirstByStatus(FlockStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<Flock> getAllFlocks() {
        return flockRepository.findAllByOrderByStartedAtDesc();
    }

    @Transactional
    public Flock closeFlock(Long id) {
        Flock flock = flockRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parvada no encontrada"));

        if (!flock.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La parvada ya está cerrada");
        }

        flock.close();
        return flockRepository.save(flock);
    }
}

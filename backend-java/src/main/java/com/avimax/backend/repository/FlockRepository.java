package com.avimax.backend.repository;

import com.avimax.backend.entity.Flock;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlockRepository extends JpaRepository<Flock, Long> {
    Optional<Flock> findFirstByStatusOrderByStartedAtDesc(com.avimax.backend.entity.FlockStatus status);

    Optional<Flock> findFirstByStatus(com.avimax.backend.entity.FlockStatus status);

    boolean existsByStatus(com.avimax.backend.entity.FlockStatus status);

    List<Flock> findAllByOrderByStartedAtDesc();
}

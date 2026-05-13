package com.avimax.backend.repository;

import com.avimax.backend.entity.CriadoraProgramming;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CriadoraProgrammingRepository extends JpaRepository<CriadoraProgramming, Long> {
    Optional<CriadoraProgramming> findByCriadoraId(Long criadoraId);
}

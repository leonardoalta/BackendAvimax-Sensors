package com.avimax.backend.repository;

import com.avimax.backend.entity.BombaProgramming;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BombaProgrammingRepository extends JpaRepository<BombaProgramming, Long> {
    Optional<BombaProgramming> findByBombaId(Long bombaId);
}

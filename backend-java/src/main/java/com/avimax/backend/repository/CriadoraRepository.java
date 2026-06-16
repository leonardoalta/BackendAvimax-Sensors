package com.avimax.backend.repository;

import com.avimax.backend.entity.Criadora;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CriadoraRepository extends JpaRepository<Criadora, Long> {
    List<Criadora> findAllByOrderByCreatedAtDesc();
    List<Criadora> findAllByOrderByCreatedAtAsc();
    Optional<Criadora> findByCodeName(String codeName);
}

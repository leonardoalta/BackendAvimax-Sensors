package com.avimax.backend.repository;

import com.avimax.backend.entity.Bomba;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BombaRepository extends JpaRepository<Bomba, Long> {
    List<Bomba> findAllByOrderByCreatedAtDesc();
    List<Bomba> findAllByOrderByCreatedAtAsc();
    Optional<Bomba> findByCodeName(String codeName);
}

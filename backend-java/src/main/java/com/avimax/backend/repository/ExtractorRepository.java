package com.avimax.backend.repository;

import com.avimax.backend.entity.Extractor;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractorRepository extends JpaRepository<Extractor, Long> {
    List<Extractor> findAllByOrderByCreatedAtDesc();
    List<Extractor> findAllByOrderByCreatedAtAsc();
    Optional<Extractor> findByCodeName(String codeName);
}

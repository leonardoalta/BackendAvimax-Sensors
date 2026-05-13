package com.avimax.backend.repository;

import com.avimax.backend.entity.Extractor;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractorRepository extends JpaRepository<Extractor, Long> {
    List<Extractor> findAllByOrderByCreatedAtDesc();
}

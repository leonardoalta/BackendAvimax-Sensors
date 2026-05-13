package com.avimax.backend.repository;

import com.avimax.backend.entity.ExtractorProgramming;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractorProgrammingRepository extends JpaRepository<ExtractorProgramming, Long> {
    Optional<ExtractorProgramming> findByExtractorId(Long extractorId);
}

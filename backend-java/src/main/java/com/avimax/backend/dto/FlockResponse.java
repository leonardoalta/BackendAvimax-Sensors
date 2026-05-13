package com.avimax.backend.dto;

import com.avimax.backend.entity.Flock;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record FlockResponse(
        Long id,
        String name,
    Integer totalBirds,
    Integer maleCount,
    Integer femaleCount,
    LocalDate flockDate,
    String birdLot,
        String notes,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt
) {
    public static FlockResponse fromEntity(Flock flock) {
        return new FlockResponse(
                flock.getId(),
                flock.getName(),
            flock.getTotalBirds(),
            flock.getMaleCount(),
            flock.getFemaleCount(),
            flock.getFlockDate(),
            flock.getBirdLot(),
                flock.getNotes(),
                flock.getStatus().name(),
                flock.getStartedAt(),
                flock.getEndedAt()
        );
    }
}

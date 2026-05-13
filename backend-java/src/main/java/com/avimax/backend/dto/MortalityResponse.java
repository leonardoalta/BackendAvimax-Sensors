package com.avimax.backend.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record MortalityResponse(
        Long id,
        LocalDate recordDate,
        Integer ageDays,
        Integer maleCount,
        Integer femaleCount,
        Integer totalCount,
        String observations,
        OffsetDateTime createdAt
) {
    public static MortalityResponse of(Long id, LocalDate recordDate, Integer ageDays, Integer maleCount, Integer femaleCount, Integer totalCount, String observations, OffsetDateTime createdAt) {
        return new MortalityResponse(id, recordDate, ageDays, maleCount, femaleCount, totalCount, observations, createdAt);
    }
}

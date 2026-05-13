package com.avimax.backend.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public class ConsumptionResponse {

    private Long id;
    private Long flockId;
    private Integer age;
    private LocalDate recordDate;
    private Double totalConsumptionKg;
    private Integer birdsCountUsed;
    private Double consumptionPerBirdKg;
    private OffsetDateTime createdAt;

    public ConsumptionResponse() {
    }

    public ConsumptionResponse(Long id, Long flockId, Integer age, LocalDate recordDate,
                               Double totalConsumptionKg, Integer birdsCountUsed,
                               Double consumptionPerBirdKg, OffsetDateTime createdAt) {
        this.id = id;
        this.flockId = flockId;
        this.age = age;
        this.recordDate = recordDate;
        this.totalConsumptionKg = totalConsumptionKg;
        this.birdsCountUsed = birdsCountUsed;
        this.consumptionPerBirdKg = consumptionPerBirdKg;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFlockId() {
        return flockId;
    }

    public void setFlockId(Long flockId) {
        this.flockId = flockId;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public LocalDate getRecordDate() {
        return recordDate;
    }

    public void setRecordDate(LocalDate recordDate) {
        this.recordDate = recordDate;
    }

    public Double getTotalConsumptionKg() {
        return totalConsumptionKg;
    }

    public void setTotalConsumptionKg(Double totalConsumptionKg) {
        this.totalConsumptionKg = totalConsumptionKg;
    }

    public Integer getBirdsCountUsed() {
        return birdsCountUsed;
    }

    public void setBirdsCountUsed(Integer birdsCountUsed) {
        this.birdsCountUsed = birdsCountUsed;
    }

    public Double getConsumptionPerBirdKg() {
        return consumptionPerBirdKg;
    }

    public void setConsumptionPerBirdKg(Double consumptionPerBirdKg) {
        this.consumptionPerBirdKg = consumptionPerBirdKg;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

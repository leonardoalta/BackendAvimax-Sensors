package com.avimax.backend.dto;

import java.time.LocalDate;

public class CreateConsumptionRequest {

    private Integer age;
    private LocalDate recordDate;
    private Double totalConsumptionKg;

    public CreateConsumptionRequest() {
    }

    public CreateConsumptionRequest(Integer age, LocalDate recordDate, Double totalConsumptionKg) {
        this.age = age;
        this.recordDate = recordDate;
        this.totalConsumptionKg = totalConsumptionKg;
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
}

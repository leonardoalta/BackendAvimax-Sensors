package com.avimax.backend.dto;

import com.avimax.backend.entity.WeightRecord.Gender;
import com.avimax.backend.entity.WeightRecord.WeightLocation;
import java.time.LocalDate;

public class CreateWeightRequest {
    private Integer sampledBirdsCount;
    private Double averageWeight; // en gramos
    private Integer age; // edad en días
    private LocalDate recordDate;
    private Gender gender;
    private WeightLocation location;

    // Constructors
    public CreateWeightRequest() {
    }

    public CreateWeightRequest(Integer sampledBirdsCount, Double averageWeight,
                              Integer age, LocalDate recordDate, Gender gender, WeightLocation location) {
        this.sampledBirdsCount = sampledBirdsCount;
        this.averageWeight = averageWeight;
        this.age = age;
        this.recordDate = recordDate;
        this.gender = gender;
        this.location = location;
    }

    // Getters and Setters
    public Integer getSampledBirdsCount() {
        return sampledBirdsCount;
    }

    public void setSampledBirdsCount(Integer sampledBirdsCount) {
        this.sampledBirdsCount = sampledBirdsCount;
    }

    public Double getAverageWeight() {
        return averageWeight;
    }

    public void setAverageWeight(Double averageWeight) {
        this.averageWeight = averageWeight;
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

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public WeightLocation getLocation() {
        return location;
    }

    public void setLocation(WeightLocation location) {
        this.location = location;
    }
}

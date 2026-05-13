package com.avimax.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "weight_records")
public class WeightRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "flock_id", nullable = false, foreignKey = @ForeignKey(name = "fk_weight_record_flock"))
    private Flock flock;

    @Column(name = "sampled_birds_count", nullable = false)
    private Integer sampledBirdsCount;

    @Column(name = "average_weight", nullable = false)
    private Double averageWeight; // en gramos

    @Column(name = "age", nullable = false)
    private Integer age; // edad en días

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WeightLocation location;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    // Constructors
    public WeightRecord() {
    }

    public WeightRecord(Flock flock, Integer sampledBirdsCount, Double averageWeight,
                       Integer age, LocalDate recordDate, Gender gender, WeightLocation location) {
        this.flock = flock;
        this.sampledBirdsCount = sampledBirdsCount;
        this.averageWeight = averageWeight;
        this.age = age;
        this.recordDate = recordDate;
        this.gender = gender;
        this.location = location;
        this.createdAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Flock getFlock() {
        return flock;
    }

    public void setFlock(Flock flock) {
        this.flock = flock;
    }

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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // Enums
    public enum Gender {
        MALE, FEMALE
    }

    public enum WeightLocation {
        PANEL, ENMEDIO, EXTRACTORES
    }
}

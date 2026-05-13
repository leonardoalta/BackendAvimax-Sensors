package com.avimax.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "consumption_records")
public class ConsumptionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "flock_id", nullable = false, foreignKey = @ForeignKey(name = "fk_consumption_record_flock"))
    private Flock flock;

    @Column(name = "age", nullable = false)
    private Integer age;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Column(name = "total_consumption_kg", nullable = false)
    private Double totalConsumptionKg;

    @Column(name = "birds_count_used", nullable = false)
    private Integer birdsCountUsed;

    @Column(name = "consumption_per_bird_kg", nullable = false)
    private Double consumptionPerBirdKg;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected ConsumptionRecord() {
    }

    public ConsumptionRecord(Flock flock, Integer age, LocalDate recordDate,
                             Double totalConsumptionKg, Integer birdsCountUsed, Double consumptionPerBirdKg) {
        this.flock = flock;
        this.age = age;
        this.recordDate = recordDate;
        this.totalConsumptionKg = totalConsumptionKg;
        this.birdsCountUsed = birdsCountUsed;
        this.consumptionPerBirdKg = consumptionPerBirdKg;
        this.createdAt = OffsetDateTime.now();
    }

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

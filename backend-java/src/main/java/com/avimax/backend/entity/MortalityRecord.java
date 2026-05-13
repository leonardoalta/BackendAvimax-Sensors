package com.avimax.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "mortality_records")
public class MortalityRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flock_id", nullable = false)
    private com.avimax.backend.entity.Flock flock;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Column(name = "age_days", nullable = false)
    private Integer ageDays;

    @Column(name = "male_count", nullable = false)
    private Integer maleCount;

    @Column(name = "female_count", nullable = false)
    private Integer femaleCount;

    @Column(name = "total_count", nullable = false)
    private Integer totalCount;

    @Column(name = "observations", length = 1000)
    private String observations;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected MortalityRecord() {
    }

    public MortalityRecord(com.avimax.backend.entity.Flock flock, Integer maleCount, Integer femaleCount, String observations) {
        this.flock = flock;
        this.recordDate = LocalDate.now();
        this.ageDays = Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(flock.getFlockDate(), this.recordDate));
        this.maleCount = maleCount;
        this.femaleCount = femaleCount;
        this.totalCount = (maleCount == null ? 0 : maleCount) + (femaleCount == null ? 0 : femaleCount);
        this.observations = observations;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public com.avimax.backend.entity.Flock getFlock() {
        return flock;
    }

    public LocalDate getRecordDate() {
        return recordDate;
    }

    public Integer getAgeDays() {
        return ageDays;
    }

    public Integer getMaleCount() {
        return maleCount;
    }

    public Integer getFemaleCount() {
        return femaleCount;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public String getObservations() {
        return observations;
    }

    public void setObservations(String observations) {
        this.observations = observations;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}

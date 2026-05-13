package com.avimax.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "flocks")
public class Flock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "total_birds", nullable = false)
    private Integer totalBirds;

    @Column(name = "male_count", nullable = false)
    private Integer maleCount;

    @Column(name = "female_count", nullable = false)
    private Integer femaleCount;

    @Column(name = "flock_date", nullable = false)
    private LocalDate flockDate;

    @Column(name = "bird_lot", nullable = false, length = 80)
    private String birdLot;

    @Column(length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FlockStatus status = FlockStatus.ACTIVE;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt = OffsetDateTime.now();

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    protected Flock() {
    }

    public Flock(String name, Integer totalBirds, Integer maleCount, Integer femaleCount, LocalDate flockDate, String birdLot, String notes) {
        this.name = name;
        this.totalBirds = totalBirds;
        this.maleCount = maleCount;
        this.femaleCount = femaleCount;
        this.flockDate = flockDate;
        this.birdLot = birdLot;
        this.notes = notes;
        this.status = FlockStatus.ACTIVE;
        this.startedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Integer getTotalBirds() {
        return totalBirds;
    }

    public Integer getMaleCount() {
        return maleCount;
    }

    public Integer getFemaleCount() {
        return femaleCount;
    }

    public LocalDate getFlockDate() {
        return flockDate;
    }

    public String getBirdLot() {
        return birdLot;
    }

    public String getNotes() {
        return notes;
    }

    public FlockStatus getStatus() {
        return status;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getEndedAt() {
        return endedAt;
    }

    public boolean isActive() {
        return status == FlockStatus.ACTIVE;
    }

    public void close() {
        this.status = FlockStatus.CLOSED;
        this.endedAt = OffsetDateTime.now();
    }

    public void reduceCounts(Integer maleDeaths, Integer femaleDeaths) {
        int md = maleDeaths == null ? 0 : maleDeaths;
        int fd = femaleDeaths == null ? 0 : femaleDeaths;
        this.maleCount = Math.max(0, this.maleCount - md);
        this.femaleCount = Math.max(0, this.femaleCount - fd);
        this.totalBirds = Math.max(0, this.totalBirds - (md + fd));
    }
}

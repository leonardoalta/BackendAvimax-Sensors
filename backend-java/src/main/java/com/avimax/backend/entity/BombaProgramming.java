package com.avimax.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "bomba_programming")
public class BombaProgramming {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bomba_id", nullable = false, unique = true)
    private Bomba bomba;

    @Column(name = "temperature_on", nullable = false)
    private Double temperatureOn;

    @Column(name = "temperature_off", nullable = false)
    private Double temperatureOff;

    @Column(name = "work_duration_seconds", nullable = false)
    private Integer workDurationSeconds;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected BombaProgramming() {
    }

    public BombaProgramming(Bomba bomba, Double temperatureOn, Double temperatureOff, Integer workDurationSeconds) {
        this.bomba = bomba;
        this.temperatureOn = temperatureOn;
        this.temperatureOff = temperatureOff;
        this.workDurationSeconds = workDurationSeconds;
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Bomba getBomba() {
        return bomba;
    }

    public Double getTemperatureOn() {
        return temperatureOn;
    }

    public Double getTemperatureOff() {
        return temperatureOff;
    }

    public Integer getWorkDurationSeconds() {
        return workDurationSeconds;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void update(Double temperatureOn, Double temperatureOff, Integer workDurationSeconds) {
        this.temperatureOn = temperatureOn;
        this.temperatureOff = temperatureOff;
        this.workDurationSeconds = workDurationSeconds;
        this.updatedAt = OffsetDateTime.now();
    }
}

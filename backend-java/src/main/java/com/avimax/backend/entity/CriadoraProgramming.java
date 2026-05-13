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
@Table(name = "criadora_programming")
public class CriadoraProgramming {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criadora_id", nullable = false, unique = true)
    private Criadora criadora;

    @Column(name = "temperature_on", nullable = false)
    private Double temperatureOn;

    @Column(name = "temperature_off", nullable = false)
    private Double temperatureOff;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected CriadoraProgramming() {
    }

    public CriadoraProgramming(Criadora criadora, Double temperatureOn, Double temperatureOff) {
        this.criadora = criadora;
        this.temperatureOn = temperatureOn;
        this.temperatureOff = temperatureOff;
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Criadora getCriadora() {
        return criadora;
    }

    public Double getTemperatureOn() {
        return temperatureOn;
    }

    public Double getTemperatureOff() {
        return temperatureOff;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void update(Double temperatureOn, Double temperatureOff) {
        this.temperatureOn = temperatureOn;
        this.temperatureOff = temperatureOff;
        this.updatedAt = OffsetDateTime.now();
    }
}

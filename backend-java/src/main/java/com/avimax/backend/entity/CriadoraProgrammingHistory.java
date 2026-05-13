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
import java.time.OffsetDateTime;

@Entity
@Table(name = "criadora_programming_history")
public class CriadoraProgrammingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criadora_id", nullable = false)
    private Criadora criadora;

    @Column(name = "actuator_name", nullable = true, length = 100)
    private String actuatorName;

    @Column(name = "actuator_type", nullable = true, length = 20)
    private String actuatorType;

    @Column(name = "temperature_on", nullable = false)
    private Double temperatureOn;

    @Column(name = "temperature_off", nullable = false)
    private Double temperatureOff;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt = OffsetDateTime.now();

    protected CriadoraProgrammingHistory() {
    }

    public CriadoraProgrammingHistory(Criadora criadora, Double temperatureOn, Double temperatureOff) {
        this.criadora = criadora;
        this.actuatorName = criadora.getName();
        this.actuatorType = "CRIADORA";
        this.temperatureOn = temperatureOn;
        this.temperatureOff = temperatureOff;
        this.recordedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Criadora getCriadora() {
        return criadora;
    }

    public String getActuatorName() {
        return actuatorName;
    }

    public String getActuatorType() {
        return actuatorType;
    }

    public Double getTemperatureOn() {
        return temperatureOn;
    }

    public Double getTemperatureOff() {
        return temperatureOff;
    }

    public OffsetDateTime getRecordedAt() {
        return recordedAt;
    }
}

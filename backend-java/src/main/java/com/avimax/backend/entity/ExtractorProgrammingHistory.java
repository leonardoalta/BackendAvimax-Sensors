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
@Table(name = "extractor_programming_history")
public class ExtractorProgrammingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "extractor_id", nullable = false)
    private Extractor extractor;

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

    protected ExtractorProgrammingHistory() {
    }

    public ExtractorProgrammingHistory(Extractor extractor, Double temperatureOn, Double temperatureOff) {
        this.extractor = extractor;
        this.actuatorName = extractor.getName();
        this.actuatorType = "EXTRACTOR";
        this.temperatureOn = temperatureOn;
        this.temperatureOff = temperatureOff;
        this.recordedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Extractor getExtractor() {
        return extractor;
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

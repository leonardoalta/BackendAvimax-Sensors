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
@Table(name = "extractor_programming")
public class ExtractorProgramming {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "extractor_id", nullable = false, unique = true)
    private Extractor extractor;

    @Column(name = "temperature_on", nullable = false)
    private Double temperatureOn;

    @Column(name = "temperature_off", nullable = false)
    private Double temperatureOff;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected ExtractorProgramming() {
    }

    public ExtractorProgramming(Extractor extractor, Double temperatureOn, Double temperatureOff) {
        this.extractor = extractor;
        this.temperatureOn = temperatureOn;
        this.temperatureOff = temperatureOff;
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Extractor getExtractor() {
        return extractor;
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

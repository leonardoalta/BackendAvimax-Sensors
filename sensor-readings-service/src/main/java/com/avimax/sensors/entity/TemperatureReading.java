package com.avimax.sensors.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "temperature_readings")
public class TemperatureReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Double value; // en grados Celsius

    @Column(nullable = false)
    private String location; // e.g., "galpon1"

    @Column(nullable = false)
    private OffsetDateTime recordedAt = OffsetDateTime.now();

    protected TemperatureReading() {
    }

    public TemperatureReading(Double value, String location) {
        this.value = value;
        this.location = location;
        this.recordedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Double getValue() {
        return value;
    }

    public String getLocation() {
        return location;
    }

    public OffsetDateTime getRecordedAt() {
        return recordedAt;
    }
}

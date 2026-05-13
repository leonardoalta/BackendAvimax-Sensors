package com.avimax.sensors.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "humidity_readings")
public class HumidityReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Double value; // en porcentaje (0-100)

    @Column(nullable = false)
    private String location; // e.g., "galpon1"

    @Column(nullable = false)
    private OffsetDateTime recordedAt = OffsetDateTime.now();

    protected HumidityReading() {
    }

    public HumidityReading(Double value, String location) {
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

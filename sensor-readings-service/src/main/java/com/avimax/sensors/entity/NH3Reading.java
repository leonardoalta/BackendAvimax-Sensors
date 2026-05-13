package com.avimax.sensors.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "nh3_readings")
public class NH3Reading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Double value; // en ppm (partes por millón)

    @Column(nullable = false)
    private String location; // e.g., "galpon1"

    @Column(nullable = false)
    private OffsetDateTime recordedAt = OffsetDateTime.now();

    protected NH3Reading() {
    }

    public NH3Reading(Double value, String location) {
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

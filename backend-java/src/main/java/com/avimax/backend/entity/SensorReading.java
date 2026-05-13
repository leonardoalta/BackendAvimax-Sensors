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
@Table(name = "sensor_readings")
public class SensorReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flock_id", nullable = false)
    private Flock flock;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @Column(name = "gateway_id", length = 80)
    private String gatewayId;

    @Column(name = "source_topic", length = 255)
    private String sourceTopic;

    @Column(name = "temperature_c")
    private Double temperatureC;

    @Column(name = "humidity_percent")
    private Double humidityPercent;

    @Column(name = "nh3_ppm")
    private Double nh3Ppm;

    protected SensorReading() {
    }

    public SensorReading(Flock flock, OffsetDateTime recordedAt, String gatewayId, String sourceTopic, Double temperatureC, Double humidityPercent, Double nh3Ppm) {
        this.flock = flock;
        this.recordedAt = recordedAt;
        this.gatewayId = gatewayId;
        this.sourceTopic = sourceTopic;
        this.temperatureC = temperatureC;
        this.humidityPercent = humidityPercent;
        this.nh3Ppm = nh3Ppm;
    }

    public Long getId() {
        return id;
    }

    public Flock getFlock() {
        return flock;
    }

    public OffsetDateTime getRecordedAt() {
        return recordedAt;
    }

    public String getGatewayId() {
        return gatewayId;
    }

    public String getSourceTopic() {
        return sourceTopic;
    }

    public Double getTemperatureC() {
        return temperatureC;
    }

    public Double getHumidityPercent() {
        return humidityPercent;
    }

    public Double getNh3Ppm() {
        return nh3Ppm;
    }
}

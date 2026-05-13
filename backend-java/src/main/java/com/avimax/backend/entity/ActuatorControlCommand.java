package com.avimax.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "actuator_control_commands")
public class ActuatorControlCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actuator_type", nullable = false, length = 20)
    private String actuatorType;

    @Column(name = "actuator_id", nullable = false)
    private Long actuatorId;

    @Column(name = "actuator_name", nullable = false, length = 100)
    private String actuatorName;

    @Column(nullable = false, length = 3)
    private String command;

    @Column(name = "temperature_c")
    private Double temperatureC;

    @Column(name = "humidity_percent")
    private Double humidityPercent;

    @Column(name = "nh3_ppm")
    private Double nh3Ppm;

    @Column(length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "dispatched_at")
    private OffsetDateTime dispatchedAt;

    @Column(name = "work_duration_seconds")
    private Integer workDurationSeconds;

    protected ActuatorControlCommand() {
    }

    public ActuatorControlCommand(String actuatorType, Long actuatorId, String actuatorName, String command, Double temperatureC, Double humidityPercent, Double nh3Ppm, String reason, Integer workDurationSeconds) {
        this.actuatorType = actuatorType;
        this.actuatorId = actuatorId;
        this.actuatorName = actuatorName;
        this.command = command;
        this.temperatureC = temperatureC;
        this.humidityPercent = humidityPercent;
        this.nh3Ppm = nh3Ppm;
        this.reason = reason;
        this.workDurationSeconds = workDurationSeconds;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getActuatorType() {
        return actuatorType;
    }

    public Long getActuatorId() {
        return actuatorId;
    }

    public String getActuatorName() {
        return actuatorName;
    }

    public String getCommand() {
        return command;
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

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getDispatchedAt() {
        return dispatchedAt;
    }

    public Integer getWorkDurationSeconds() {
        return workDurationSeconds;
    }

    public void markDispatched() {
        this.dispatchedAt = OffsetDateTime.now();
    }
}

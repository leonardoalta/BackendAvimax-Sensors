package com.avimax.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "alarm_rules")
public class AlarmRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlarmVariable variable;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false, length = 20)
    private AlarmCondition conditionType;

    @Column(nullable = false)
    private Double threshold;

    @Column(nullable = false, length = 10)
    private String unit;

    @Column(name = "minimum_duration_seconds", nullable = false)
    private Integer minimumDurationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlarmSeverity severity;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AlarmRule() {
    }

    public AlarmRule(String name,
                     AlarmVariable variable,
                     AlarmCondition conditionType,
                     Double threshold,
                     String unit,
                     Integer minimumDurationSeconds,
                     AlarmSeverity severity,
                     String message,
                     boolean active) {
        this.name = name;
        this.variable = variable;
        this.conditionType = conditionType;
        this.threshold = threshold;
        this.unit = unit;
        this.minimumDurationSeconds = minimumDurationSeconds;
        this.severity = severity;
        this.message = message;
        this.active = active;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String name,
                       AlarmVariable variable,
                       AlarmCondition conditionType,
                       Double threshold,
                       String unit,
                       Integer minimumDurationSeconds,
                       AlarmSeverity severity,
                       String message) {
        this.name = name;
        this.variable = variable;
        this.conditionType = conditionType;
        this.threshold = threshold;
        this.unit = unit;
        this.minimumDurationSeconds = minimumDurationSeconds;
        this.severity = severity;
        this.message = message;
        this.updatedAt = OffsetDateTime.now();
    }

    public void setActive(boolean active) {
        this.active = active;
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public AlarmVariable getVariable() {
        return variable;
    }

    public AlarmCondition getConditionType() {
        return conditionType;
    }

    public Double getThreshold() {
        return threshold;
    }

    public String getUnit() {
        return unit;
    }

    public Integer getMinimumDurationSeconds() {
        return minimumDurationSeconds;
    }

    public AlarmSeverity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}

package com.avimax.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "alarms")
public class Alarm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    private AlarmRule rule;

    @Column(name = "rule_name", nullable = false, length = 120)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlarmVariable variable;

    @Column(name = "detected_value", nullable = false)
    private Double detectedValue;

    @Column(nullable = false)
    private Double threshold;

    @Column(nullable = false, length = 10)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false, length = 20)
    private AlarmCondition conditionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlarmSeverity severity;

    @Column(nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlarmStatus status;

    @Column(name = "activated_at", nullable = false)
    private OffsetDateTime activatedAt;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    protected Alarm() {
    }

    public Alarm(AlarmRule rule,
                 String ruleName,
                 AlarmVariable variable,
                 Double detectedValue,
                 Double threshold,
                 String unit,
                 AlarmCondition conditionType,
                 AlarmSeverity severity,
                 String message,
                 OffsetDateTime activatedAt) {
        this.rule = rule;
        this.ruleName = ruleName;
        this.variable = variable;
        this.detectedValue = detectedValue;
        this.threshold = threshold;
        this.unit = unit;
        this.conditionType = conditionType;
        this.severity = severity;
        this.message = message;
        this.status = AlarmStatus.ACTIVA;
        this.activatedAt = activatedAt;
    }

    public void setDetectedValue(Double detectedValue) {
        this.detectedValue = detectedValue;
    }

    public void recognize(OffsetDateTime acknowledgedAt) {
        this.status = AlarmStatus.RECONOCIDA;
        this.acknowledgedAt = acknowledgedAt;
    }

    public void resolve(OffsetDateTime resolvedAt) {
        this.status = AlarmStatus.RESUELTA;
        this.resolvedAt = resolvedAt;
    }

    public void close(OffsetDateTime closedAt) {
        this.status = AlarmStatus.CERRADA;
        this.closedAt = closedAt;
    }

    public Long getId() {
        return id;
    }

    public AlarmRule getRule() {
        return rule;
    }

    public String getRuleName() {
        return ruleName;
    }

    public AlarmVariable getVariable() {
        return variable;
    }

    public Double getDetectedValue() {
        return detectedValue;
    }

    public Double getThreshold() {
        return threshold;
    }

    public String getUnit() {
        return unit;
    }

    public AlarmCondition getConditionType() {
        return conditionType;
    }

    public AlarmSeverity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public AlarmStatus getStatus() {
        return status;
    }

    public OffsetDateTime getActivatedAt() {
        return activatedAt;
    }

    public OffsetDateTime getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }
}

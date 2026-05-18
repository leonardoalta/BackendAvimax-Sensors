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
@Table(name = "alarm_rule_states")
public class AlarmRuleState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false, unique = true)
    private AlarmRule rule;

    @Column(name = "condition_met", nullable = false)
    private boolean conditionMet;

    @Column(name = "met_since")
    private OffsetDateTime metSince;

    @Column(name = "last_value")
    private Double lastValue;

    @Column(name = "last_evaluated_at")
    private OffsetDateTime lastEvaluatedAt;

    protected AlarmRuleState() {
    }

    public AlarmRuleState(AlarmRule rule) {
        this.rule = rule;
        this.conditionMet = false;
    }

    public void markConditionMet(OffsetDateTime since, Double value, OffsetDateTime evaluatedAt) {
        if (!this.conditionMet) {
            this.metSince = since;
        }
        this.conditionMet = true;
        this.lastValue = value;
        this.lastEvaluatedAt = evaluatedAt;
    }

    public void markConditionCleared(Double value, OffsetDateTime evaluatedAt) {
        this.conditionMet = false;
        this.metSince = null;
        this.lastValue = value;
        this.lastEvaluatedAt = evaluatedAt;
    }

    public Long getId() {
        return id;
    }

    public AlarmRule getRule() {
        return rule;
    }

    public boolean isConditionMet() {
        return conditionMet;
    }

    public OffsetDateTime getMetSince() {
        return metSince;
    }

    public Double getLastValue() {
        return lastValue;
    }

    public OffsetDateTime getLastEvaluatedAt() {
        return lastEvaluatedAt;
    }
}

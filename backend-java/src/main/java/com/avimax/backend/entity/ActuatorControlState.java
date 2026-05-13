package com.avimax.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "actuator_control_states")
public class ActuatorControlState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actuator_type", nullable = false, length = 20)
    private String actuatorType;

    @Column(name = "actuator_id", nullable = false)
    private Long actuatorId;

    @Column(name = "actuator_name", nullable = false, length = 100)
    private String actuatorName;

    @Column(name = "current_state", nullable = false)
    private boolean currentState;

    @Column(name = "last_updated_at", nullable = false)
    private OffsetDateTime lastUpdatedAt = OffsetDateTime.now();

    protected ActuatorControlState() {
    }

    public ActuatorControlState(String actuatorType, Long actuatorId, String actuatorName, boolean currentState) {
        this.actuatorType = actuatorType;
        this.actuatorId = actuatorId;
        this.actuatorName = actuatorName;
        this.currentState = currentState;
        this.lastUpdatedAt = OffsetDateTime.now();
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

    public boolean isCurrentState() {
        return currentState;
    }

    public OffsetDateTime getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void update(boolean currentState) {
        this.currentState = currentState;
        this.lastUpdatedAt = OffsetDateTime.now();
    }
}

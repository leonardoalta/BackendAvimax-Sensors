package com.avimax.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class LocalManualActuatorCommandRequest {

    @NotBlank(message = "actuatorType es obligatorio")
    private String actuatorType;

    @NotNull(message = "actuatorId es obligatorio")
    private Long actuatorId;

    @NotBlank(message = "action es obligatoria")
    private String action;

    private String reason;

    private Integer workDurationSeconds;

    public String getActuatorType() { return actuatorType; }
    public void setActuatorType(String actuatorType) { this.actuatorType = actuatorType; }

    public Long getActuatorId() { return actuatorId; }
    public void setActuatorId(Long actuatorId) { this.actuatorId = actuatorId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Integer getWorkDurationSeconds() { return workDurationSeconds; }
    public void setWorkDurationSeconds(Integer workDurationSeconds) { this.workDurationSeconds = workDurationSeconds; }
}

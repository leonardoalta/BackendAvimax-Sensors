package com.avimax.backend.dto;

import java.util.List;

/**
 * DTO para deserializar el snapshot de bootstrap recibido del backend central.
 * Se mapea directamente desde el JSON MQTT.
 */
public class BootstrapSnapshotDto {

    private String requestId;
    private String snapshotId;
    private Long galponId;
    private String gatewayCode;
    private Long configVersion;
    private String generatedAt;
    private List<ActuatorDto> actuators;

    public BootstrapSnapshotDto() {}

    public String getRequestId()         { return requestId; }
    public void setRequestId(String v)   { this.requestId = v; }

    public String getSnapshotId()        { return snapshotId; }
    public void setSnapshotId(String v)  { this.snapshotId = v; }

    public Long getGalponId()            { return galponId; }
    public void setGalponId(Long v)      { this.galponId = v; }

    public String getGatewayCode()       { return gatewayCode; }
    public void setGatewayCode(String v) { this.gatewayCode = v; }

    public Long getConfigVersion()       { return configVersion; }
    public void setConfigVersion(Long v) { this.configVersion = v; }

    public String getGeneratedAt()       { return generatedAt; }
    public void setGeneratedAt(String v) { this.generatedAt = v; }

    public List<ActuatorDto> getActuators()         { return actuators; }
    public void setActuators(List<ActuatorDto> v)   { this.actuators = v; }

    public static class ActuatorDto {
        private Long centralActuatorId;
        private String actuatorType;
        private String codeName;
        private String name;
        private boolean enabled;
        private String state;
        private ProgrammingDto programming;

        public ActuatorDto() {}

        public Long getCentralActuatorId()              { return centralActuatorId; }
        public void setCentralActuatorId(Long v)        { this.centralActuatorId = v; }

        public String getActuatorType()                 { return actuatorType; }
        public void setActuatorType(String v)           { this.actuatorType = v; }

        public String getCodeName()                     { return codeName; }
        public void setCodeName(String v)               { this.codeName = v; }

        public String getName()                         { return name; }
        public void setName(String v)                   { this.name = v; }

        public boolean isEnabled()                      { return enabled; }
        public void setEnabled(boolean v)               { this.enabled = v; }

        public String getState()                        { return state; }
        public void setState(String v)                  { this.state = v; }

        public ProgrammingDto getProgramming()          { return programming; }
        public void setProgramming(ProgrammingDto v)    { this.programming = v; }
    }

    public static class ProgrammingDto {
        private Double temperatureOn;
        private Double temperatureOff;
        private Integer workDurationSeconds;

        public ProgrammingDto() {}

        public Double getTemperatureOn()                { return temperatureOn; }
        public void setTemperatureOn(Double v)          { this.temperatureOn = v; }

        public Double getTemperatureOff()               { return temperatureOff; }
        public void setTemperatureOff(Double v)         { this.temperatureOff = v; }

        public Integer getWorkDurationSeconds()         { return workDurationSeconds; }
        public void setWorkDurationSeconds(Integer v)   { this.workDurationSeconds = v; }
    }
}

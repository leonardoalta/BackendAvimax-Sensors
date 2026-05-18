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
@Table(name = "alarm_events")
public class AlarmEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alarm_id", nullable = false)
    private Alarm alarm;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private AlarmEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private AlarmStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private AlarmStatus newStatus;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "event_at", nullable = false)
    private OffsetDateTime eventAt;

    protected AlarmEvent() {
    }

    public AlarmEvent(Alarm alarm,
                      AlarmEventType eventType,
                      AlarmStatus previousStatus,
                      AlarmStatus newStatus,
                      String description,
                      OffsetDateTime eventAt) {
        this.alarm = alarm;
        this.eventType = eventType;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.description = description;
        this.eventAt = eventAt;
    }

    public Long getId() {
        return id;
    }

    public Alarm getAlarm() {
        return alarm;
    }

    public AlarmEventType getEventType() {
        return eventType;
    }

    public AlarmStatus getPreviousStatus() {
        return previousStatus;
    }

    public AlarmStatus getNewStatus() {
        return newStatus;
    }

    public String getDescription() {
        return description;
    }

    public OffsetDateTime getEventAt() {
        return eventAt;
    }
}

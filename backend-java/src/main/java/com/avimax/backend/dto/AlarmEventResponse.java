package com.avimax.backend.dto;

import com.avimax.backend.entity.AlarmEvent;
import java.time.OffsetDateTime;

public record AlarmEventResponse(
        Long idEvento,
        Long idAlarma,
        String tipoEvento,
        String estadoAnterior,
        String estadoNuevo,
        String descripcion,
        OffsetDateTime fechaEvento
) {
    public static AlarmEventResponse fromEntity(AlarmEvent event) {
        return new AlarmEventResponse(
                event.getId(),
                event.getAlarm().getId(),
                event.getEventType().name(),
                event.getPreviousStatus() != null ? event.getPreviousStatus().name().toLowerCase() : null,
                event.getNewStatus().name().toLowerCase(),
                event.getDescription(),
                event.getEventAt()
        );
    }
}

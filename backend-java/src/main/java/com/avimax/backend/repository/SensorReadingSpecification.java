package com.avimax.backend.repository;

import com.avimax.backend.entity.SensorReading;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.domain.Specification;

public class SensorReadingSpecification {

    private SensorReadingSpecification() {}

    public static Specification<SensorReading> withFilters(
            OffsetDateTime start,
            OffsetDateTime end,
            String variable,
            String gateway,
            String sensor,
            Long flockId) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<Predicate>();

            // Filtro por parvada (obligatorio para aislar datos)
            if (flockId != null && flockId > 0) {
                predicates.add(cb.equal(root.get("flock").get("id"), flockId));
            }

            // Filtro de rango de fechas
            if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("recordedAt"), start));
            }
            if (end != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("recordedAt"), end));
            }

            // Filtro por gateway
            if (gateway != null && !gateway.isEmpty()) {
                predicates.add(cb.equal(root.get("gatewayId"), gateway));
            }

            // Filtro por sensor
            if (sensor != null && !sensor.isEmpty()) {
                predicates.add(cb.like(root.get("sourceTopic"), "%" + sensor + "%"));
            }

            // Nota: el filtro por "variable" (temperatura, humedad, nh3)
            // se maneja en la respuesta (en el DTO se omiten campos nulos según variable)
            // La query devuelve todos los registros y el DTO filtra qué mostrar

            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

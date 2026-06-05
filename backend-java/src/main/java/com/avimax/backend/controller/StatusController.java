package com.avimax.backend.controller;

import com.avimax.backend.dto.MqttStatusResponse;
import com.avimax.backend.service.MqttIngestionService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de estado para monitorear la salud del sistema.
 * Permite que el dashboard sepa si los sensores están publicando datos en el broker.
 */
@RestController
@RequestMapping("/api/status")
public class StatusController {

        private final ObjectProvider<MqttIngestionService> mqttIngestionServiceProvider;

        public StatusController(ObjectProvider<MqttIngestionService> mqttIngestionServiceProvider) {
                this.mqttIngestionServiceProvider = mqttIngestionServiceProvider;
    }

    /**
     * GET /api/status/mqtt
     * Devuelve el estado actual de la conexión MQTT.
     * 
     * Respuesta:
     * {
     *   "connected": true,
     *   "subscribedTopic": "avicola/galpon1/lecturas",
     *   "lastMessageReceivedAt": "2026-06-04T10:30:15.123456Z",
     *   "totalMessagesReceived": 1234,
     *   "connectionStatus": "CONNECTED",
     *   "lastError": null,
     *   "lastErrorAt": null,
     *   "brokerUrl": "tcp://192.168.1.100:1883"
     * }
     *
     * Estados posibles:
     * - CONNECTED: conexión activa, recibiendo mensajes
     * - DISCONNECTED: sin conexión activa
     * - CONNECTING_ERROR: error en la última conexión
     */
    @GetMapping("/mqtt")
    public ResponseEntity<MqttStatusResponse> getMqttStatus() {
                return ResponseEntity.ok(resolveStatus());
    }

    /**
     * GET /api/status/health
     * Comprobación simple de salud.
     * Si MQTT está habilitado, devuelve el estado MQTT.
     * Si MQTT está deshabilitado, devuelve OK.
     */
    @GetMapping("/health")
    public ResponseEntity<?> getHealth() {
        try {
                        MqttStatusResponse mqttStatus = resolveStatus();
            
            // Si MQTT está conectado, todo bien
            if (mqttStatus.connected()) {
                return ResponseEntity.ok()
                        .body(new HealthResponse(
                                "UP",
                                "Sistema operativo. MQTT conectado.",
                                mqttStatus
                        ));
            }
            
            // Si MQTT está desconectado pero recientemente, es una reconexión en progreso
            if (mqttStatus.lastMessageReceivedAt() != null) {
                return ResponseEntity.ok()
                        .body(new HealthResponse(
                                "DEGRADED",
                                "MQTT desconectado pero con datos previos recibidos.",
                                mqttStatus
                        ));
            }
            
            // Si nunca recibió mensajes, hay problema
            return ResponseEntity.ok()
                    .body(new HealthResponse(
                            "DOWN",
                            "MQTT sin conexión. Los sensores no están publicando datos.",
                            mqttStatus
                    ));
        } catch (Exception e) {
            return ResponseEntity.status(503)
                    .body(new HealthResponse(
                            "ERROR",
                            "Error al verificar estado: " + e.getMessage(),
                            null
                    ));
        }
    }

    /**
     * Respuesta de salud del sistema
     */
    public record HealthResponse(
            String status,
            String message,
            MqttStatusResponse mqtt
    ) {}

        private MqttStatusResponse resolveStatus() {
                MqttIngestionService service = mqttIngestionServiceProvider.getIfAvailable();
                if (service != null) {
                        return service.getStatus();
                }

                return new MqttStatusResponse(
                                false,
                                null,
                                null,
                                0L,
                                MqttStatusResponse.ConnectionStatus.DISCONNECTED.name(),
                                "MQTT deshabilitado o servicio no disponible",
                                OffsetDateTime.now(ZoneOffset.UTC),
                                null
                );
        }
}

package com.avimax.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/**
 * Estado actual de la conexión MQTT y del broker.
 * Permite al frontend/dashboard saber si los sensores están publicando datos.
 */
public record MqttStatusResponse(
        @JsonProperty("connected")
        Boolean connected,

        @JsonProperty("subscribedTopic")
        String subscribedTopic,

        @JsonProperty("lastMessageReceivedAt")
        OffsetDateTime lastMessageReceivedAt,

        @JsonProperty("totalMessagesReceived")
        Long totalMessagesReceived,

        @JsonProperty("connectionStatus")
        String connectionStatus,

        @JsonProperty("lastError")
        String lastError,

        @JsonProperty("lastErrorAt")
        OffsetDateTime lastErrorAt,

        @JsonProperty("brokerUrl")
        String brokerUrl
) {

    /**
     * Estados posibles de conexión
     */
    public enum ConnectionStatus {
        CONNECTED("Conectado al broker"),
        DISCONNECTED("Desconectado del broker"),
        CONNECTING_ERROR("Error al conectar"),
        INITIALIZING("Inicializando conexión");

        private final String label;

        ConnectionStatus(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }
}

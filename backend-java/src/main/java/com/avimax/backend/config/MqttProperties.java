package com.avimax.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mqtt")
public record MqttProperties(
        boolean enabled,
        String brokerUrl,
        String clientId,
        String topic,
        String username,
        String password,
        int qos,
        int connectionTimeoutSeconds,
        int keepAliveSeconds
) {
    public MqttProperties {
        if (clientId == null || clientId.isBlank()) {
            clientId = "avimax-backend";
        }
        if (topic == null || topic.isBlank()) {
            topic = "avicola/galpon1/lecturas";
        }
        if (qos < 0) {
            qos = 1;
        }
        if (connectionTimeoutSeconds <= 0) {
            connectionTimeoutSeconds = 10;
        }
        if (keepAliveSeconds <= 0) {
            keepAliveSeconds = 60;
        }
    }
}

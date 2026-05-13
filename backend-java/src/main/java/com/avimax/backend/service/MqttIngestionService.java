package com.avimax.backend.service;

import com.avimax.backend.config.MqttProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true", matchIfMissing = false)
public class MqttIngestionService implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttIngestionService.class);

    private static final String FIELD_GATEWAY_ID = "gateway_id";
    private static final String FIELD_TIMESTAMP = "timestamp";
    private static final String FIELD_TEMPERATURE = "temperature";
    private static final String FIELD_HUMIDITY = "humidity";
    private static final String FIELD_NH3 = "nh3";
    private static final String FIELD_READINGS = "readings";
    private static final String FIELD_TEMPERATURA_C = "temperatura_c";
    private static final String FIELD_HUMEDAD_RELATIVA = "humedad_relativa";
    private static final String FIELD_NH3_PPM = "nh3_ppm";
    private static final String FIELD_AMONIACO = "amoniaco";
    private static final double DEFAULT_NH3_PPM = 0.0d;

    private final ObjectMapper objectMapper;
    private final SensorReadingService sensorReadingService;
    private final MqttProperties mqttProperties;

    private MqttClient client;

    public MqttIngestionService(ObjectMapper objectMapper, SensorReadingService sensorReadingService, MqttProperties mqttProperties) {
        this.objectMapper = objectMapper;
        this.sensorReadingService = sensorReadingService;
        this.mqttProperties = mqttProperties;
    }

    @PostConstruct
    public void start() {
        try {
            client = new MqttClient(mqttProperties.brokerUrl(), mqttProperties.clientId() + "-ingest", new MemoryPersistence());
            client.setCallback(this);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(mqttProperties.connectionTimeoutSeconds());
            options.setKeepAliveInterval(mqttProperties.keepAliveSeconds());

            if (mqttProperties.username() != null && !mqttProperties.username().isBlank()) {
                options.setUserName(mqttProperties.username());
            }
            if (mqttProperties.password() != null && !mqttProperties.password().isBlank()) {
                options.setPassword(mqttProperties.password().toCharArray());
            }

            client.connect(options);
            client.subscribe(mqttProperties.topic(), mqttProperties.qos());
            log.info("MQTT conectado y suscrito a {}", mqttProperties.topic());
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible iniciar la suscripción MQTT", e);
        }
    }

    @PreDestroy
    public void stop() {
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
            }
            if (client != null) {
                client.close();
            }
        } catch (Exception e) {
            log.warn("Error cerrando cliente MQTT", e);
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("Conexión MQTT completa. reconnect={}, serverURI={}", reconnect, serverURI);
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("Conexión MQTT perdida", cause);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            processPayload(topic, new String(message.getPayload()));
        } catch (Exception e) {
            log.error("Error procesando mensaje MQTT", e);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // No aplica porque este backend solo consume lecturas.
    }

    private void processPayload(String topic, String payload) throws Exception {
        JsonNode root = objectMapper.readTree(payload);

        String gatewayId = textOrNull(root, FIELD_GATEWAY_ID);
        OffsetDateTime recordedAt = parseTimestamp(root.path(FIELD_TIMESTAMP).asText(null));

        ReadingValues values = extractValues(root);
        sensorReadingService.saveIfActiveFlock(
                gatewayId,
                topic,
                recordedAt,
                values.temperature(),
                values.humidity(),
            values.nh3() != null ? values.nh3() : DEFAULT_NH3_PPM
        );
    }

    private ReadingValues extractValues(JsonNode root) {
        if (hasDirectValues(root)) {
            return new ReadingValues(
                    readDouble(root, FIELD_TEMPERATURE),
                    readDouble(root, FIELD_HUMIDITY),
                    readDouble(root, FIELD_NH3)
            );
        }

        if (hasReadingsArray(root)) {
            return extractFromReadings(root.get(FIELD_READINGS));
        }

        return new ReadingValues(null, null, null);
    }

    private ReadingValues extractFromReadings(JsonNode readings) {
        Double temperature = null;
        Double humidity = null;
        Double nh3 = null;

        for (JsonNode reading : readings) {
            temperature = firstNonNull(temperature, firstDouble(reading, FIELD_TEMPERATURE, FIELD_TEMPERATURA_C));
            humidity = firstNonNull(humidity, firstDouble(reading, FIELD_HUMIDITY, FIELD_HUMEDAD_RELATIVA));
            nh3 = firstNonNull(nh3, firstDouble(reading, FIELD_NH3, FIELD_NH3_PPM, FIELD_AMONIACO));
        }

        return new ReadingValues(temperature, humidity, nh3);
    }

    private boolean hasDirectValues(JsonNode root) {
        return root.has(FIELD_TEMPERATURE) || root.has(FIELD_HUMIDITY) || root.has(FIELD_NH3);
    }

    private boolean hasReadingsArray(JsonNode root) {
        return root.has(FIELD_READINGS) && root.get(FIELD_READINGS).isArray();
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private Double readDouble(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asDouble();
    }

    private Double firstDouble(JsonNode node, String... fields) {
        for (String field : fields) {
            Double value = readDouble(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private OffsetDateTime parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }

        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    private Double firstNonNull(Double current, Double candidate) {
        return current != null ? current : candidate;
    }

    private record ReadingValues(Double temperature, Double humidity, Double nh3) {
    }
}

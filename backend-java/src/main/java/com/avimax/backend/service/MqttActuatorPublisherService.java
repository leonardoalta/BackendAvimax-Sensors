package com.avimax.backend.service;

import com.avimax.backend.config.MqttProperties;
import com.avimax.backend.entity.ActuatorControlState;
import com.avimax.backend.entity.Bomba;
import com.avimax.backend.entity.Criadora;
import com.avimax.backend.entity.Extractor;
import com.avimax.backend.repository.ActuatorControlStateRepository;
import com.avimax.backend.repository.BombaRepository;
import com.avimax.backend.repository.CriadoraRepository;
import com.avimax.backend.repository.ExtractorRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
public class MqttActuatorPublisherService {

    private static final Logger log = LoggerFactory.getLogger(MqttActuatorPublisherService.class);

    private static final String PREFIX = "avimax/actuator";
    private static final String AGGREGATE_TOPIC = "avimax/actuators/state";
    private static final String SOURCE = "backend-control";

    private final ObjectMapper objectMapper;
    private final MqttProperties mqttProperties;
    private final ExtractorRepository extractorRepository;
    private final CriadoraRepository criadoraRepository;
    private final BombaRepository bombaRepository;
    private final ActuatorControlStateRepository actuatorControlStateRepository;

    private MqttClient client;

    public MqttActuatorPublisherService(ObjectMapper objectMapper,
                                        MqttProperties mqttProperties,
                                        ExtractorRepository extractorRepository,
                                        CriadoraRepository criadoraRepository,
                                        BombaRepository bombaRepository,
                                        ActuatorControlStateRepository actuatorControlStateRepository) {
        this.objectMapper = objectMapper;
        this.mqttProperties = mqttProperties;
        this.extractorRepository = extractorRepository;
        this.criadoraRepository = criadoraRepository;
        this.bombaRepository = bombaRepository;
        this.actuatorControlStateRepository = actuatorControlStateRepository;
    }

    @PostConstruct
    public void start() {
        try {
            client = new MqttClient(mqttProperties.brokerUrl(), mqttProperties.clientId() + "-actuator", new MemoryPersistence());
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
            publishCurrentStateSnapshot();
            log.info("MQTT actuator publisher conectado a {}", mqttProperties.brokerUrl());
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible iniciar el publicador MQTT de actuadores", e);
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
            log.warn("Error cerrando publicador MQTT de actuadores", e);
        }
    }

    public void publishCurrentStateSnapshot() {
        try {
            publishTypeSnapshot(
                    "EXTRACTOR",
                    "fan",
                    "E",
                    10,
                    extractorRepository.findAllByOrderByCreatedAtDesc(),
                    Extractor::getId,
                    Extractor::getName
            );
            publishTypeSnapshot(
                    "CRIADORA",
                    "heater",
                    "C",
                    5,
                    criadoraRepository.findAllByOrderByCreatedAtDesc(),
                    Criadora::getId,
                    Criadora::getName
            );
            publishTypeSnapshot(
                    "BOMBA",
                    "pump",
                    "B",
                    2,
                    bombaRepository.findAllByOrderByCreatedAtDesc(),
                    Bomba::getId,
                    Bomba::getName
            );

            publishAggregateState();
        } catch (Exception e) {
            log.error("Error publicando snapshot de estados de actuadores", e);
        }
    }

    public void publishStateChange(String actuatorType, int number, Long actuatorId, String actuatorName, boolean state) {
        try {
            String label = labelFor(actuatorType, number);
            String topic = unitTopic(actuatorType, number);

            Map<String, Object> payload = new HashMap<>();
            payload.put("type", actuatorType);
            payload.put("number", number);
            payload.put("index", number - 1);
            payload.put("label", label);
            payload.put("name", actuatorName);
            payload.put("state", state);
            payload.put("source", SOURCE);
            payload.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());

            publishEnvelope(topic, payload);
            publishAggregateState();
        } catch (Exception e) {
            log.error("Error publicando cambio de estado de actuador {} {}", actuatorType, actuatorId, e);
        }
    }

    private <T> void publishTypeSnapshot(String actuatorType,
                                         String topicType,
                                         String labelPrefix,
                                         int expectedCount,
                                         List<T> actuators,
                                         Function<T, Long> idExtractor,
                                         Function<T, String> nameExtractor) throws Exception {
        for (int i = 1; i <= expectedCount; i++) {
            boolean state = false;
            Long actuatorId = null;
            String actuatorName = labelPrefix + i;
            if (i <= actuators.size()) {
                T actuator = actuators.get(i - 1);
                actuatorId = idExtractor.apply(actuator);
                actuatorName = nameExtractor.apply(actuator);
                state = actuatorControlStateRepository
                        .findByActuatorTypeAndActuatorId(actuatorType, actuatorId)
                        .map(ActuatorControlState::isCurrentState)
                        .orElse(false);
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("type", topicType);
            payload.put("number", i);
            payload.put("index", i - 1);
            payload.put("label", labelPrefix + i);
            payload.put("name", actuatorName);
            payload.put("actuatorId", actuatorId);
            payload.put("state", state);
            payload.put("source", SOURCE);
            payload.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
            publishEnvelope(unitTopic(topicType, i), payload);
        }
    }

    private void publishAggregateState() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("fans", buildStates("EXTRACTOR", 10, extractorRepository.findAllByOrderByCreatedAtDesc(), Extractor::getId));
        payload.put("heaters", buildStates("CRIADORA", 5, criadoraRepository.findAllByOrderByCreatedAtDesc(), Criadora::getId));
        payload.put("pumps", buildStates("BOMBA", 2, bombaRepository.findAllByOrderByCreatedAtDesc(), Bomba::getId));
        payload.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
        payload.put("source", SOURCE);

        publishEnvelope(AGGREGATE_TOPIC, payload);
    }

    private <T> List<Boolean> buildStates(String actuatorType, int expectedCount, List<T> actuators, Function<T, Long> idExtractor) {
        List<Boolean> states = new ArrayList<>(expectedCount);
        for (int i = 1; i <= expectedCount; i++) {
            boolean state = false;
            if (i <= actuators.size()) {
                T actuator = actuators.get(i - 1);
                Long actuatorId = idExtractor.apply(actuator);
                state = actuatorControlStateRepository.findByActuatorTypeAndActuatorId(actuatorType, actuatorId)
                        .map(ActuatorControlState::isCurrentState)
                        .orElse(false);
            }
            states.add(state);
        }
        return states;
    }

    private String labelFor(String actuatorType, int number) {
        String prefix = switch (actuatorType) {
            case "EXTRACTOR" -> "E";
            case "CRIADORA" -> "C";
            case "BOMBA" -> "B";
            default -> "A";
        };
        return prefix + number;
    }

    private String unitTopic(String actuatorType, int number) {
        String type = switch (actuatorType) {
            case "EXTRACTOR" -> "fan";
            case "CRIADORA" -> "heater";
            case "BOMBA" -> "pump";
            default -> actuatorType.toLowerCase();
        };
        return PREFIX + "/" + type + "/" + number + "/state";
    }

    private void publishEnvelope(String topic, Map<String, Object> payload) throws Exception {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("topic", topic);
        envelope.put("payload", payload);
        envelope.put("meta", Map.of("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond()));

        byte[] body = objectMapper.writeValueAsBytes(envelope);
        MqttMessage message = new MqttMessage(body);
        message.setQos(mqttProperties.qos());
        message.setRetained(true);
        client.publish(topic, message);
    }
}
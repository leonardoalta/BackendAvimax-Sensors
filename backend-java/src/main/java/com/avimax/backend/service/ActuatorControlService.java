package com.avimax.backend.service;

import com.avimax.backend.dto.ActuatorSignalResponse;
import com.avimax.backend.dto.ControlCountsResponse;
import com.avimax.backend.dto.ControlEvaluationResponse;
import com.avimax.backend.dto.LocalManualActuatorCommandRequest;
import com.avimax.backend.dto.LocalManualActuatorCommandResponse;
import com.avimax.backend.entity.ActuatorControlCommand;
import com.avimax.backend.entity.ActuatorControlState;
import com.avimax.backend.entity.Bomba;
import com.avimax.backend.entity.BombaProgramming;
import com.avimax.backend.entity.Criadora;
import com.avimax.backend.entity.CriadoraProgramming;
import com.avimax.backend.entity.Extractor;
import com.avimax.backend.entity.ExtractorProgramming;
import com.avimax.backend.entity.SensorReading;
import com.avimax.backend.repository.ActuatorControlCommandRepository;
import com.avimax.backend.repository.ActuatorControlStateRepository;
import com.avimax.backend.repository.BombaProgrammingRepository;
import com.avimax.backend.repository.BombaRepository;
import com.avimax.backend.repository.CriadoraProgrammingRepository;
import com.avimax.backend.repository.CriadoraRepository;
import com.avimax.backend.repository.ExtractorProgrammingRepository;
import com.avimax.backend.repository.ExtractorRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ActuatorControlService {

    private static final Logger log = LoggerFactory.getLogger(ActuatorControlService.class);

    static final String TYPE_EXTRACTOR   = "EXTRACTOR";
    static final String TYPE_CRIADORA    = "CRIADORA";
    static final String TYPE_BOMBA       = "BOMBA";
    private static final String TRIGGERED_MANUAL_LOCAL = "MANUAL_LOCAL";

    private final ExtractorRepository extractorRepository;
    private final ExtractorProgrammingRepository extractorProgrammingRepository;
    private final CriadoraRepository criadoraRepository;
    private final CriadoraProgrammingRepository criadoraProgrammingRepository;
    private final BombaRepository bombaRepository;
    private final BombaProgrammingRepository bombaProgrammingRepository;
    private final ActuatorControlStateRepository actuatorControlStateRepository;
    private final ActuatorControlCommandRepository actuatorControlCommandRepository;
    private final MqttActuatorPublisherService mqttActuatorPublisherService;

    @Autowired
    private LocalSyncMqttPublisherService syncPublisher;

    private final ScheduledExecutorService autoOffScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "actuator-auto-off");
        t.setDaemon(true);
        return t;
    });

    public ActuatorControlService(
            ExtractorRepository extractorRepository,
            ExtractorProgrammingRepository extractorProgrammingRepository,
            CriadoraRepository criadoraRepository,
            CriadoraProgrammingRepository criadoraProgrammingRepository,
            BombaRepository bombaRepository,
            BombaProgrammingRepository bombaProgrammingRepository,
            ActuatorControlStateRepository actuatorControlStateRepository,
                ActuatorControlCommandRepository actuatorControlCommandRepository,
                MqttActuatorPublisherService mqttActuatorPublisherService
    ) {
        this.extractorRepository = extractorRepository;
        this.extractorProgrammingRepository = extractorProgrammingRepository;
        this.criadoraRepository = criadoraRepository;
        this.criadoraProgrammingRepository = criadoraProgrammingRepository;
        this.bombaRepository = bombaRepository;
        this.bombaProgrammingRepository = bombaProgrammingRepository;
        this.actuatorControlStateRepository = actuatorControlStateRepository;
        this.actuatorControlCommandRepository = actuatorControlCommandRepository;
        this.mqttActuatorPublisherService = mqttActuatorPublisherService;
    }

    @Transactional
    public ControlEvaluationResponse evaluateAndQueue(SensorReading reading) {
        Double temperature = reading.getTemperatureC();
        Double humidity = reading.getHumidityPercent();
        Double nh3 = reading.getNh3Ppm();

        List<ActuatorSignalResponse> signals = new ArrayList<>();

        evaluateExtractors(temperature, humidity, nh3, signals);
        evaluateCriadoras(temperature, humidity, nh3, signals);
        evaluateBombas(temperature, humidity, nh3, signals);

        mqttActuatorPublisherService.publishCurrentStateSnapshot();

        return new ControlEvaluationResponse(
                OffsetDateTime.now(),
                temperature,
                humidity,
                nh3,
                new ControlCountsResponse(
                actuatorControlStateRepository.countByActuatorType(TYPE_EXTRACTOR),
                        actuatorControlStateRepository.countByActuatorTypeAndCurrentStateTrue(TYPE_EXTRACTOR),
                actuatorControlStateRepository.countByActuatorType(TYPE_CRIADORA),
                        actuatorControlStateRepository.countByActuatorTypeAndCurrentStateTrue(TYPE_CRIADORA),
                actuatorControlStateRepository.countByActuatorType(TYPE_BOMBA),
                        actuatorControlStateRepository.countByActuatorTypeAndCurrentStateTrue(TYPE_BOMBA)
                ),
                signals
        );
    }

    @Transactional(readOnly = true)
    public List<ActuatorControlCommand> getPendingCommands() {
        return actuatorControlCommandRepository.findByDispatchedAtIsNullOrderByCreatedAtAsc();
    }

    @Transactional
    public ActuatorControlCommand markDispatched(Long commandId) {
        ActuatorControlCommand command = actuatorControlCommandRepository.findById(commandId)
                .orElseThrow(() -> new IllegalArgumentException("Command no encontrado"));
        command.markDispatched();
        return actuatorControlCommandRepository.save(command);
    }

    /**
     * Aplica un comando externo (recibido del central) sobre un actuador local.
     * Actualiza ActuatorControlState y publica el nuevo estado al bridge MQTT/WebSocket.
     * No genera ActuatorControlCommand ni dispara evaluación de reglas.
     */
    @Transactional
    public void applyExternalCommand(String actuatorType, Long actuatorId, String action,
                                     String triggeredBy, Integer workDurationSeconds) {
        boolean desiredState = "ON".equalsIgnoreCase(action);
        String name = resolveActuatorName(actuatorType, actuatorId);
        String displayName = name != null ? name : actuatorType + "-" + actuatorId;
        int slotNumber = resolveSlotNumber(actuatorType, actuatorId);

        ActuatorControlState state = actuatorControlStateRepository
                .findByActuatorTypeAndActuatorId(actuatorType, actuatorId)
                .orElseGet(() -> new ActuatorControlState(actuatorType, actuatorId, displayName, desiredState));
        state.update(desiredState);
        actuatorControlStateRepository.save(state);

        mqttActuatorPublisherService.publishStateChange(actuatorType, slotNumber, actuatorId, displayName, desiredState);
        syncPublisher.publishActuatorStateChanged(state, triggeredBy, workDurationSeconds);
        scheduleAutoOff(actuatorType, actuatorId, action, workDurationSeconds);
    }

    public LocalManualActuatorCommandResponse applyLocalManualCommand(LocalManualActuatorCommandRequest request) {
        String upperType = request.getActuatorType() != null ? request.getActuatorType().toUpperCase() : "";
        if (!TYPE_EXTRACTOR.equals(upperType) && !TYPE_CRIADORA.equals(upperType) && !TYPE_BOMBA.equals(upperType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "actuatorType inválido: " + request.getActuatorType() + ". Válidos: EXTRACTOR, CRIADORA, BOMBA");
        }

        String action = request.getAction() != null ? request.getAction().toUpperCase() : "";
        if (!"ON".equals(action) && !"OFF".equals(action)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "action inválida: " + request.getAction() + ". Válidas: ON, OFF");
        }

        if (request.getWorkDurationSeconds() != null && request.getWorkDurationSeconds() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workDurationSeconds debe ser positivo");
        }

        boolean exists = switch (upperType) {
            case TYPE_EXTRACTOR -> extractorRepository.existsById(request.getActuatorId());
            case TYPE_CRIADORA  -> criadoraRepository.existsById(request.getActuatorId());
            case TYPE_BOMBA     -> bombaRepository.existsById(request.getActuatorId());
            default -> false;
        };
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Actuador " + upperType + " con id=" + request.getActuatorId() + " no existe");
        }

        String reason = (request.getReason() != null && !request.getReason().isBlank())
                ? request.getReason() : TRIGGERED_MANUAL_LOCAL;

        applyExternalCommand(upperType, request.getActuatorId(), action, TRIGGERED_MANUAL_LOCAL,
                request.getWorkDurationSeconds());

        ActuatorControlState state = actuatorControlStateRepository
                .findByActuatorTypeAndActuatorId(upperType, request.getActuatorId())
                .orElseThrow(() -> new IllegalStateException("Estado no encontrado tras aplicar comando"));

        return new LocalManualActuatorCommandResponse(
                state.getActuatorType(),
                state.getActuatorId(),
                state.getActuatorName(),
                action,
                state.isCurrentState(),
                reason,
                TRIGGERED_MANUAL_LOCAL,
                request.getWorkDurationSeconds(),
                state.getLastUpdatedAt()
        );
    }

    private void scheduleAutoOff(String actuatorType, Long actuatorId, String action, Integer workDurationSeconds) {
        if (!TYPE_BOMBA.equals(actuatorType)
                || !"ON".equalsIgnoreCase(action)
                || workDurationSeconds == null || workDurationSeconds <= 0) {
            return;
        }
        final int delay = workDurationSeconds;
        autoOffScheduler.schedule(() -> {
            try {
                applyExternalCommand(TYPE_BOMBA, actuatorId, "OFF", "AUTO_OFF", null);
                log.info("[ActuatorControl] Auto-OFF ejecutado — BOMBA {} tras {}s", actuatorId, delay);
            } catch (Exception e) {
                log.error("[ActuatorControl] Error en auto-OFF de BOMBA {}: {}", actuatorId, e.getMessage());
            }
        }, delay, TimeUnit.SECONDS);
        log.info("[ActuatorControl] Auto-OFF de BOMBA {} programado en {}s", actuatorId, delay);
    }

    private String resolveActuatorName(String type, Long actuatorId) {
        return switch (type.toUpperCase()) {
            case TYPE_EXTRACTOR -> extractorRepository.findById(actuatorId).map(Extractor::getName).orElse(null);
            case TYPE_CRIADORA  -> criadoraRepository.findById(actuatorId).map(Criadora::getName).orElse(null);
            case TYPE_BOMBA     -> bombaRepository.findById(actuatorId).map(Bomba::getName).orElse(null);
            default -> null;
        };
    }

    private int resolveSlotNumber(String type, Long actuatorId) {
        List<Long> ids = switch (type.toUpperCase()) {
            case TYPE_EXTRACTOR -> extractorRepository.findAllByOrderByCreatedAtDesc()
                    .stream().map(Extractor::getId).toList();
            case TYPE_CRIADORA  -> criadoraRepository.findAllByOrderByCreatedAtDesc()
                    .stream().map(Criadora::getId).toList();
            case TYPE_BOMBA     -> bombaRepository.findAllByOrderByCreatedAtDesc()
                    .stream().map(Bomba::getId).toList();
            default -> List.of();
        };
        int idx = ids.indexOf(actuatorId);
        return idx >= 0 ? idx + 1 : 1;
    }

    private void evaluateExtractors(Double temperature, Double humidity, Double nh3, List<ActuatorSignalResponse> signals) {
        List<Extractor> extractors = extractorRepository.findAllByOrderByCreatedAtDesc();
        for (int i = 0; i < extractors.size(); i++) {
            Extractor extractor = extractors.get(i);
            ExtractorProgramming programming = extractorProgrammingRepository.findByExtractorId(extractor.getId()).orElse(null);
            if (programming == null || temperature == null) {
                continue;
            }
            evaluateOne(TYPE_EXTRACTOR, i + 1, extractor.getId(), extractor.getName(), temperature, humidity, nh3, programming.getTemperatureOn(), programming.getTemperatureOff(), null, signals);
        }
    }

    private void evaluateCriadoras(Double temperature, Double humidity, Double nh3, List<ActuatorSignalResponse> signals) {
        List<Criadora> criadoras = criadoraRepository.findAllByOrderByCreatedAtDesc();
        for (int i = 0; i < criadoras.size(); i++) {
            Criadora criadora = criadoras.get(i);
            CriadoraProgramming programming = criadoraProgrammingRepository.findByCriadoraId(criadora.getId()).orElse(null);
            if (programming == null || temperature == null) {
                continue;
            }
            evaluateOne(TYPE_CRIADORA, i + 1, criadora.getId(), criadora.getName(), temperature, humidity, nh3, programming.getTemperatureOn(), programming.getTemperatureOff(), null, signals);
        }
    }

    private void evaluateBombas(Double temperature, Double humidity, Double nh3, List<ActuatorSignalResponse> signals) {
        List<Bomba> bombas = bombaRepository.findAllByOrderByCreatedAtDesc();
        for (int i = 0; i < bombas.size(); i++) {
            Bomba bomba = bombas.get(i);
            BombaProgramming programming = bombaProgrammingRepository.findByBombaId(bomba.getId()).orElse(null);
            if (programming == null || temperature == null) {
                continue;
            }
            evaluateOne(TYPE_BOMBA, i + 1, bomba.getId(), bomba.getName(), temperature, humidity, nh3, programming.getTemperatureOn(), programming.getTemperatureOff(), programming.getWorkDurationSeconds(), signals);
        }
    }

    private void evaluateOne(
            String actuatorType,
            int slotNumber,
            Long actuatorId,
            String actuatorName,
            Double temperature,
            Double humidity,
            Double nh3,
            Double temperatureOn,
            Double temperatureOff,
            Integer workDurationSeconds,
            List<ActuatorSignalResponse> signals
    ) {
        boolean currentState = actuatorControlStateRepository
                .findByActuatorTypeAndActuatorId(actuatorType, actuatorId)
                .map(ActuatorControlState::isCurrentState)
                .orElse(false);

        boolean desiredState = currentState;
        if (temperature >= temperatureOn) {
            desiredState = true;
        } else if (temperature <= temperatureOff) {
            desiredState = false;
        }

        if (desiredState == currentState) {
            return;
        }

        final boolean finalDesiredState = desiredState;
        ActuatorControlState state = actuatorControlStateRepository
            .findByActuatorTypeAndActuatorId(actuatorType, actuatorId)
            .orElseGet(() -> new ActuatorControlState(actuatorType, actuatorId, actuatorName, finalDesiredState));
        state.update(desiredState);
        actuatorControlStateRepository.save(state);
        mqttActuatorPublisherService.publishStateChange(actuatorType, slotNumber, actuatorId, actuatorName, desiredState);
        syncPublisher.publishActuatorStateChanged(state, "LOCAL_EVALUATION", workDurationSeconds);

        String command = desiredState ? "ON" : "OFF";
        String reason = "temperature=" + temperature + ", on=" + temperatureOn + ", off=" + temperatureOff;
        ActuatorControlCommand stored = actuatorControlCommandRepository.save(
                new ActuatorControlCommand(
                        actuatorType,
                        actuatorId,
                        actuatorName,
                        command,
                        temperature,
                        humidity,
                        nh3,
                        reason,
                        workDurationSeconds
                )
        );

        signals.add(ActuatorSignalResponse.of(
                stored.getId(),
                stored.getActuatorType(),
                stored.getActuatorId(),
                stored.getActuatorName(),
                stored.getCommand(),
                stored.getWorkDurationSeconds(),
                stored.getReason(),
                stored.getCreatedAt()
        ));
    }
}

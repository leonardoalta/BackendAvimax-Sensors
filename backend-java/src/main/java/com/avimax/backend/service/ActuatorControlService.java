package com.avimax.backend.service;

import com.avimax.backend.dto.ActuatorSignalResponse;
import com.avimax.backend.dto.ControlCountsResponse;
import com.avimax.backend.dto.ControlEvaluationResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActuatorControlService {

    private final ExtractorRepository extractorRepository;
    private final ExtractorProgrammingRepository extractorProgrammingRepository;
    private final CriadoraRepository criadoraRepository;
    private final CriadoraProgrammingRepository criadoraProgrammingRepository;
    private final BombaRepository bombaRepository;
    private final BombaProgrammingRepository bombaProgrammingRepository;
    private final ActuatorControlStateRepository actuatorControlStateRepository;
    private final ActuatorControlCommandRepository actuatorControlCommandRepository;
    private final MqttActuatorPublisherService mqttActuatorPublisherService;

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
                actuatorControlStateRepository.countByActuatorType("EXTRACTOR"),
                        actuatorControlStateRepository.countByActuatorTypeAndCurrentStateTrue("EXTRACTOR"),
                actuatorControlStateRepository.countByActuatorType("CRIADORA"),
                        actuatorControlStateRepository.countByActuatorTypeAndCurrentStateTrue("CRIADORA"),
                actuatorControlStateRepository.countByActuatorType("BOMBA"),
                        actuatorControlStateRepository.countByActuatorTypeAndCurrentStateTrue("BOMBA")
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

    private void evaluateExtractors(Double temperature, Double humidity, Double nh3, List<ActuatorSignalResponse> signals) {
        List<Extractor> extractors = extractorRepository.findAllByOrderByCreatedAtDesc();
        for (int i = 0; i < extractors.size(); i++) {
            Extractor extractor = extractors.get(i);
            ExtractorProgramming programming = extractorProgrammingRepository.findByExtractorId(extractor.getId()).orElse(null);
            if (programming == null || temperature == null) {
                continue;
            }
            evaluateOne("EXTRACTOR", i + 1, extractor.getId(), extractor.getName(), temperature, humidity, nh3, programming.getTemperatureOn(), programming.getTemperatureOff(), null, signals);
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
            evaluateOne("CRIADORA", i + 1, criadora.getId(), criadora.getName(), temperature, humidity, nh3, programming.getTemperatureOn(), programming.getTemperatureOff(), null, signals);
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
            evaluateOne("BOMBA", i + 1, bomba.getId(), bomba.getName(), temperature, humidity, nh3, programming.getTemperatureOn(), programming.getTemperatureOff(), programming.getWorkDurationSeconds(), signals);
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

package com.avimax.backend.service;

import com.avimax.backend.dto.BootstrapSnapshotDto;
import com.avimax.backend.dto.BootstrapSnapshotDto.ActuatorDto;
import com.avimax.backend.dto.BootstrapSnapshotDto.ProgrammingDto;
import com.avimax.backend.entity.*;
import com.avimax.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Fase 2 — aplica el snapshot de bootstrap recibido del central.
 *
 * Regla de upsert: clave = (actuatorType + codeName).
 * - Si existe localmente: actualiza nombre, enabled, centralActuatorId, programación.
 * - Si no existe: crea actuador + programación.
 * - Si existe localmente pero no viene en snapshot: deshabilita (no elimina).
 */
@Service
public class BootstrapSnapshotApplyService {

    private static final Logger log = LoggerFactory.getLogger(BootstrapSnapshotApplyService.class);

    private static final String SYNC_SOURCE_BOOTSTRAP  = "BOOTSTRAP";
    private static final String SYNC_SOURCE_DISABLED   = "DISABLED_BY_BOOTSTRAP";

    @Autowired private ExtractorRepository extractorRepository;
    @Autowired private CriadoraRepository  criadoraRepository;
    @Autowired private BombaRepository     bombaRepository;
    @Autowired private ExtractorProgrammingRepository extractorProgrammingRepository;
    @Autowired private CriadoraProgrammingRepository  criadoraProgrammingRepository;
    @Autowired private BombaProgrammingRepository     bombaProgrammingRepository;

    public record ApplyResult(
            int received,
            int created,
            int updated,
            int disabled,
            String errorMessage
    ) {
        public boolean isSuccess() { return errorMessage == null; }
    }

    @Transactional
    public ApplyResult apply(BootstrapSnapshotDto snapshot) {
        if (snapshot == null || snapshot.getActuators() == null) {
            return new ApplyResult(0, 0, 0, 0, "Snapshot nulo o sin actuadores");
        }

        int created = 0, updated = 0, disabled = 0;
        Set<String> snapshotExtractorCodes = new HashSet<>();
        Set<String> snapshotCriadoraCodes  = new HashSet<>();
        Set<String> snapshotBombaCodes     = new HashSet<>();

        try {
            for (ActuatorDto dto : snapshot.getActuators()) {
                if (dto.getCodeName() == null || dto.getActuatorType() == null) {
                    log.warn("[Bootstrap] Actuador sin codeName o tipo en snapshot — omitido");
                    continue;
                }

                String type = dto.getActuatorType().toUpperCase();
                switch (type) {
                    case "EXTRACTOR" -> {
                        snapshotExtractorCodes.add(dto.getCodeName());
                        boolean isNew = upsertExtractor(dto);
                        if (isNew) created++; else updated++;
                    }
                    case "CRIADORA" -> {
                        snapshotCriadoraCodes.add(dto.getCodeName());
                        boolean isNew = upsertCriadora(dto);
                        if (isNew) created++; else updated++;
                    }
                    case "BOMBA" -> {
                        snapshotBombaCodes.add(dto.getCodeName());
                        boolean isNew = upsertBomba(dto);
                        if (isNew) created++; else updated++;
                    }
                    default -> log.warn("[Bootstrap] Tipo de actuador desconocido en snapshot: {}", type);
                }
            }

            // Deshabilitar actuadores locales que no vienen en snapshot
            disabled += disableOrphanExtractors(snapshotExtractorCodes);
            disabled += disableOrphanCriadoras(snapshotCriadoraCodes);
            disabled += disableOrphanBombas(snapshotBombaCodes);

            log.info("[Bootstrap] Snapshot aplicado — recibidos={} creados={} actualizados={} deshabilitados={}",
                    snapshot.getActuators().size(), created, updated, disabled);

            return new ApplyResult(snapshot.getActuators().size(), created, updated, disabled, null);

        } catch (Exception e) {
            log.error("[Bootstrap] Error aplicando snapshot: {}", e.getMessage(), e);
            return new ApplyResult(snapshot.getActuators().size(), created, updated, disabled, e.getMessage());
        }
    }

    // ─── Upsert Extractor ─────────────────────────────────────────────────────

    private boolean upsertExtractor(ActuatorDto dto) {
        Optional<Extractor> existing = extractorRepository.findByCodeName(dto.getCodeName());
        OffsetDateTime now = OffsetDateTime.now();

        if (existing.isPresent()) {
            Extractor e = existing.get();
            if (dto.getName() != null) e.setName(dto.getName());
            e.setEnabled(dto.isEnabled());
            e.setCentralActuatorId(dto.getCentralActuatorId());
            e.setLastSyncedAt(now);
            e.setSyncSource(SYNC_SOURCE_BOOTSTRAP);
            extractorRepository.save(e);
            updateExtractorProgramming(e, dto.getProgramming());
            log.debug("[Bootstrap] Extractor actualizado: {}", dto.getCodeName());
            return false;
        } else {
            Extractor e = new Extractor(dto.getName() != null ? dto.getName() : dto.getCodeName());
            e.setCodeName(dto.getCodeName());
            e.setEnabled(dto.isEnabled());
            e.setCentralActuatorId(dto.getCentralActuatorId());
            e.setLastSyncedAt(now);
            e.setSyncSource(SYNC_SOURCE_BOOTSTRAP);
            Extractor saved = extractorRepository.save(e);
            createExtractorProgramming(saved, dto.getProgramming());
            log.info("[Bootstrap] Extractor creado: {} (centralId={})", dto.getCodeName(), dto.getCentralActuatorId());
            return true;
        }
    }

    private void updateExtractorProgramming(Extractor e, ProgrammingDto prog) {
        if (prog == null || prog.getTemperatureOn() == null || prog.getTemperatureOff() == null) return;
        extractorProgrammingRepository.findByExtractorId(e.getId()).ifPresentOrElse(
                p -> { p.update(prog.getTemperatureOn(), prog.getTemperatureOff()); extractorProgrammingRepository.save(p); },
                () -> extractorProgrammingRepository.save(new ExtractorProgramming(e, prog.getTemperatureOn(), prog.getTemperatureOff()))
        );
    }

    private void createExtractorProgramming(Extractor e, ProgrammingDto prog) {
        double tempOn  = (prog != null && prog.getTemperatureOn()  != null) ? prog.getTemperatureOn()  : 28.0;
        double tempOff = (prog != null && prog.getTemperatureOff() != null) ? prog.getTemperatureOff() : 25.0;
        extractorProgrammingRepository.save(new ExtractorProgramming(e, tempOn, tempOff));
    }

    // ─── Upsert Criadora ──────────────────────────────────────────────────────

    private boolean upsertCriadora(ActuatorDto dto) {
        Optional<Criadora> existing = criadoraRepository.findByCodeName(dto.getCodeName());
        OffsetDateTime now = OffsetDateTime.now();

        if (existing.isPresent()) {
            Criadora c = existing.get();
            if (dto.getName() != null) c.setName(dto.getName());
            c.setEnabled(dto.isEnabled());
            c.setCentralActuatorId(dto.getCentralActuatorId());
            c.setLastSyncedAt(now);
            c.setSyncSource(SYNC_SOURCE_BOOTSTRAP);
            criadoraRepository.save(c);
            updateCriadoraProgramming(c, dto.getProgramming());
            log.debug("[Bootstrap] Criadora actualizada: {}", dto.getCodeName());
            return false;
        } else {
            Criadora c = new Criadora(dto.getName() != null ? dto.getName() : dto.getCodeName());
            c.setCodeName(dto.getCodeName());
            c.setEnabled(dto.isEnabled());
            c.setCentralActuatorId(dto.getCentralActuatorId());
            c.setLastSyncedAt(now);
            c.setSyncSource(SYNC_SOURCE_BOOTSTRAP);
            Criadora saved = criadoraRepository.save(c);
            createCriadoraProgramming(saved, dto.getProgramming());
            log.info("[Bootstrap] Criadora creada: {} (centralId={})", dto.getCodeName(), dto.getCentralActuatorId());
            return true;
        }
    }

    private void updateCriadoraProgramming(Criadora c, ProgrammingDto prog) {
        if (prog == null || prog.getTemperatureOn() == null || prog.getTemperatureOff() == null) return;
        criadoraProgrammingRepository.findByCriadoraId(c.getId()).ifPresentOrElse(
                p -> { p.update(prog.getTemperatureOn(), prog.getTemperatureOff()); criadoraProgrammingRepository.save(p); },
                () -> criadoraProgrammingRepository.save(new CriadoraProgramming(c, prog.getTemperatureOn(), prog.getTemperatureOff()))
        );
    }

    private void createCriadoraProgramming(Criadora c, ProgrammingDto prog) {
        double tempOn  = (prog != null && prog.getTemperatureOn()  != null) ? prog.getTemperatureOn()  : 33.0;
        double tempOff = (prog != null && prog.getTemperatureOff() != null) ? prog.getTemperatureOff() : 30.0;
        criadoraProgrammingRepository.save(new CriadoraProgramming(c, tempOn, tempOff));
    }

    // ─── Upsert Bomba ─────────────────────────────────────────────────────────

    private boolean upsertBomba(ActuatorDto dto) {
        Optional<Bomba> existing = bombaRepository.findByCodeName(dto.getCodeName());
        OffsetDateTime now = OffsetDateTime.now();

        if (existing.isPresent()) {
            Bomba b = existing.get();
            if (dto.getName() != null) b.setName(dto.getName());
            b.setEnabled(dto.isEnabled());
            b.setCentralActuatorId(dto.getCentralActuatorId());
            b.setLastSyncedAt(now);
            b.setSyncSource(SYNC_SOURCE_BOOTSTRAP);
            bombaRepository.save(b);
            updateBombaProgramming(b, dto.getProgramming());
            log.debug("[Bootstrap] Bomba actualizada: {}", dto.getCodeName());
            return false;
        } else {
            Bomba b = new Bomba(dto.getName() != null ? dto.getName() : dto.getCodeName());
            b.setCodeName(dto.getCodeName());
            b.setEnabled(dto.isEnabled());
            b.setCentralActuatorId(dto.getCentralActuatorId());
            b.setLastSyncedAt(now);
            b.setSyncSource(SYNC_SOURCE_BOOTSTRAP);
            Bomba saved = bombaRepository.save(b);
            createBombaProgramming(saved, dto.getProgramming());
            log.info("[Bootstrap] Bomba creada: {} (centralId={})", dto.getCodeName(), dto.getCentralActuatorId());
            return true;
        }
    }

    private void updateBombaProgramming(Bomba b, ProgrammingDto prog) {
        if (prog == null || prog.getTemperatureOn() == null || prog.getTemperatureOff() == null) return;
        bombaProgrammingRepository.findByBombaId(b.getId()).ifPresentOrElse(
                p -> { p.update(prog.getTemperatureOn(), prog.getTemperatureOff(), prog.getWorkDurationSeconds() != null ? prog.getWorkDurationSeconds() : p.getWorkDurationSeconds()); bombaProgrammingRepository.save(p); },
                () -> bombaProgrammingRepository.save(new BombaProgramming(b, prog.getTemperatureOn(), prog.getTemperatureOff(), prog.getWorkDurationSeconds() != null ? prog.getWorkDurationSeconds() : 300))
        );
    }

    private void createBombaProgramming(Bomba b, ProgrammingDto prog) {
        double tempOn    = (prog != null && prog.getTemperatureOn()  != null) ? prog.getTemperatureOn()  : 26.0;
        double tempOff   = (prog != null && prog.getTemperatureOff() != null) ? prog.getTemperatureOff() : 24.0;
        int    duration  = (prog != null && prog.getWorkDurationSeconds() != null) ? prog.getWorkDurationSeconds() : 300;
        bombaProgrammingRepository.save(new BombaProgramming(b, tempOn, tempOff, duration));
    }

    // ─── Disable orphans ──────────────────────────────────────────────────────

    private int disableOrphanExtractors(Set<String> snapshotCodes) {
        List<Extractor> all = extractorRepository.findAll();
        int count = 0;
        for (Extractor e : all) {
            if (e.getCodeName() != null && !snapshotCodes.contains(e.getCodeName()) && e.isEnabled()) {
                e.setEnabled(false);
                e.setSyncSource(SYNC_SOURCE_DISABLED);
                e.setLastSyncedAt(OffsetDateTime.now());
                extractorRepository.save(e);
                log.info("[Bootstrap] Extractor deshabilitado por bootstrap: {}", e.getCodeName());
                count++;
            }
        }
        return count;
    }

    private int disableOrphanCriadoras(Set<String> snapshotCodes) {
        List<Criadora> all = criadoraRepository.findAll();
        int count = 0;
        for (Criadora c : all) {
            if (c.getCodeName() != null && !snapshotCodes.contains(c.getCodeName()) && c.isEnabled()) {
                c.setEnabled(false);
                c.setSyncSource(SYNC_SOURCE_DISABLED);
                c.setLastSyncedAt(OffsetDateTime.now());
                criadoraRepository.save(c);
                log.info("[Bootstrap] Criadora deshabilitada por bootstrap: {}", c.getCodeName());
                count++;
            }
        }
        return count;
    }

    private int disableOrphanBombas(Set<String> snapshotCodes) {
        List<Bomba> all = bombaRepository.findAll();
        int count = 0;
        for (Bomba b : all) {
            if (b.getCodeName() != null && !snapshotCodes.contains(b.getCodeName()) && b.isEnabled()) {
                b.setEnabled(false);
                b.setSyncSource(SYNC_SOURCE_DISABLED);
                b.setLastSyncedAt(OffsetDateTime.now());
                bombaRepository.save(b);
                log.info("[Bootstrap] Bomba deshabilitada por bootstrap: {}", b.getCodeName());
                count++;
            }
        }
        return count;
    }
}

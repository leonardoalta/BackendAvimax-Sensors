package com.avimax.backend.config;

import com.avimax.backend.entity.*;
import com.avimax.backend.repository.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inicializa datos básicos al startup si no existen.
 * - Parvada activa (necesaria para guardar lecturas de sensores)
 * - 12 Ventiladores (Extractores) con programación y codeName EXT-01..EXT-12
 * - 5 Criadoras con programación y codeName CRI-01..CRI-05
 * - 2 Bombas con programación y codeName BOM-01..BOM-02
 *
 * Si ya existen actuadores sin codeName (instalaciones previas),
 * se les asigna uno automáticamente según orden de creación.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    /** Si es false, no se crean actuadores demo al arrancar con BD vacía. */
    @Value("${app.seed.demo-actuators:false}")
    private boolean seedDemoActuators;

    private final FlockRepository flockRepository;
    private final ExtractorRepository extractorRepository;
    private final ExtractorProgrammingRepository extractorProgrammingRepository;
    private final CriadoraRepository criadoraRepository;
    private final CriadoraProgrammingRepository criadoraProgrammingRepository;
    private final BombaRepository bombaRepository;
    private final BombaProgrammingRepository bombaProgrammingRepository;

    public DataInitializer(FlockRepository flockRepository,
                          ExtractorRepository extractorRepository,
                          ExtractorProgrammingRepository extractorProgrammingRepository,
                          CriadoraRepository criadoraRepository,
                          CriadoraProgrammingRepository criadoraProgrammingRepository,
                          BombaRepository bombaRepository,
                          BombaProgrammingRepository bombaProgrammingRepository) {
        this.flockRepository = flockRepository;
        this.extractorRepository = extractorRepository;
        this.extractorProgrammingRepository = extractorProgrammingRepository;
        this.criadoraRepository = criadoraRepository;
        this.criadoraProgrammingRepository = criadoraProgrammingRepository;
        this.bombaRepository = bombaRepository;
        this.bombaProgrammingRepository = bombaProgrammingRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (!flockRepository.existsByStatus(FlockStatus.ACTIVE)) {
            createDefaultFlock();
        }

        long extCount  = extractorRepository.count();
        long criCount  = criadoraRepository.count();
        long bomCount  = bombaRepository.count();

        if (extCount == 0 && seedDemoActuators) {
            createExtractors();
        } else if (extCount > 0) {
            // Instalaciones previas: asignar codeNames que falten
            assignExtractorCodeNamesIfMissing();
        } else {
            log.info("[DataInit] Sin extractores — esperando bootstrap del central (app.seed.demo-actuators=false)");
        }

        if (criCount == 0 && seedDemoActuators) {
            createCriadoras();
        } else if (criCount > 0) {
            assignCriadoraCodeNamesIfMissing();
        } else {
            log.info("[DataInit] Sin criadoras — esperando bootstrap del central");
        }

        if (bomCount == 0 && seedDemoActuators) {
            createBombas();
        } else if (bomCount > 0) {
            assignBombaCodeNamesIfMissing();
        } else {
            log.info("[DataInit] Sin bombas — esperando bootstrap del central");
        }

        log.info("✓ Inicialización de datos completada (seedDemoActuators={})", seedDemoActuators);
    }

    private void createDefaultFlock() {
        Flock flock = new Flock(
                "Parvada Default",
                1000,
                500,
                500,
                LocalDate.now(),
                "DEFAULT-001",
                "Parvada de prueba creada automáticamente"
        );
        flockRepository.save(flock);
        log.info("✓ Parvada activa creada automáticamente");
    }

    private void createExtractors() {
        for (int i = 1; i <= 12; i++) {
            Extractor extractor = new Extractor("Ventilador " + i);
            extractor.setCodeName(String.format("EXT-%02d", i));
            Extractor saved = extractorRepository.save(extractor);
            extractorProgrammingRepository.save(new ExtractorProgramming(saved, 28.0, 25.0));
        }
        log.info("✓ 12 Ventiladores creados con codeName EXT-01..EXT-12");
    }

    private void createCriadoras() {
        for (int i = 1; i <= 5; i++) {
            Criadora criadora = new Criadora("Criadora " + i);
            criadora.setCodeName(String.format("CRI-%02d", i));
            Criadora saved = criadoraRepository.save(criadora);
            criadoraProgrammingRepository.save(new CriadoraProgramming(saved, 33.0, 30.0));
        }
        log.info("✓ 5 Criadoras creadas con codeName CRI-01..CRI-05");
    }

    private void createBombas() {
        for (int i = 1; i <= 2; i++) {
            Bomba bomba = new Bomba("Bomba " + i);
            bomba.setCodeName(String.format("BOM-%02d", i));
            Bomba saved = bombaRepository.save(bomba);
            bombaProgrammingRepository.save(new BombaProgramming(saved, 26.0, 24.0, 300));
        }
        log.info("✓ 2 Bombas creadas con codeName BOM-01..BOM-02");
    }

    /** Asigna codeNames EXT-01..EXT-N a extractores existentes que no tienen codeName. */
    private void assignExtractorCodeNamesIfMissing() {
        List<Extractor> all = extractorRepository.findAllByOrderByCreatedAtAsc();
        Set<String> used = all.stream()
                .map(Extractor::getCodeName)
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.toSet());
        int counter = 1;
        for (Extractor e : all) {
            if (e.getCodeName() == null || e.getCodeName().isBlank()) {
                String code;
                do { code = String.format("EXT-%02d", counter++); } while (used.contains(code));
                e.setCodeName(code);
                used.add(code);
                extractorRepository.save(e);
                log.info("✓ codeName={} asignado a Extractor id={} ({})", code, e.getId(), e.getName());
            }
        }
    }

    /** Asigna codeNames CRI-01..CRI-N a criadoras existentes que no tienen codeName. */
    private void assignCriadoraCodeNamesIfMissing() {
        List<Criadora> all = criadoraRepository.findAllByOrderByCreatedAtAsc();
        Set<String> used = all.stream()
                .map(Criadora::getCodeName)
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.toSet());
        int counter = 1;
        for (Criadora c : all) {
            if (c.getCodeName() == null || c.getCodeName().isBlank()) {
                String code;
                do { code = String.format("CRI-%02d", counter++); } while (used.contains(code));
                c.setCodeName(code);
                used.add(code);
                criadoraRepository.save(c);
                log.info("✓ codeName={} asignado a Criadora id={} ({})", code, c.getId(), c.getName());
            }
        }
    }

    /** Asigna codeNames BOM-01..BOM-N a bombas existentes que no tienen codeName. */
    private void assignBombaCodeNamesIfMissing() {
        List<Bomba> all = bombaRepository.findAllByOrderByCreatedAtAsc();
        Set<String> used = all.stream()
                .map(Bomba::getCodeName)
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.toSet());
        int counter = 1;
        for (Bomba b : all) {
            if (b.getCodeName() == null || b.getCodeName().isBlank()) {
                String code;
                do { code = String.format("BOM-%02d", counter++); } while (used.contains(code));
                b.setCodeName(code);
                used.add(code);
                bombaRepository.save(b);
                log.info("✓ codeName={} asignado a Bomba id={} ({})", code, b.getId(), b.getName());
            }
        }
    }
}

package com.avimax.backend.config;

import com.avimax.backend.entity.*;
import com.avimax.backend.repository.*;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inicializa datos básicos al startup si no existen.
 * - Parvada activa (necesaria para guardar lecturas de sensores)
 * - 12 Ventiladores (Extractores) con programación
 * - 5 Criadoras con programación
 * - 2 Bombas con programación
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

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
        // Crear parvada activa si no existe
        if (!flockRepository.existsByStatus(FlockStatus.ACTIVE)) {
            createDefaultFlock();
        }

        // Crear actuadores si no existen
        if (extractorRepository.count() == 0) {
            createExtractors();
        }
        if (criadoraRepository.count() == 0) {
            createCriadoras();
        }
        if (bombaRepository.count() == 0) {
            createBombas();
        }

        log.info("✓ Inicialización de datos completada");
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
        // 12 Ventiladores
        for (int i = 1; i <= 12; i++) {
            Extractor extractor = new Extractor("Ventilador " + i);
            Extractor saved = extractorRepository.save(extractor);

            ExtractorProgramming programming = new ExtractorProgramming(saved, 28.0, 25.0);
            extractorProgrammingRepository.save(programming);
        }
        log.info("✓ 12 Ventiladores (Extractores) creados con programación");
    }

    private void createCriadoras() {
        // 5 Criadoras
        for (int i = 1; i <= 5; i++) {
            Criadora criadora = new Criadora("Criadora " + i);
            Criadora saved = criadoraRepository.save(criadora);

            CriadoraProgramming programming = new CriadoraProgramming(saved, 33.0, 30.0);
            criadoraProgrammingRepository.save(programming);
        }
        log.info("✓ 5 Criadoras creadas con programación");
    }

    private void createBombas() {
        // 2 Bombas
        for (int i = 1; i <= 2; i++) {
            Bomba bomba = new Bomba("Bomba " + i);
            Bomba saved = bombaRepository.save(bomba);

            BombaProgramming programming = new BombaProgramming(saved, 26.0, 24.0, 300);
            bombaProgrammingRepository.save(programming);
        }
        log.info("✓ 2 Bombas creadas con programación");
    }
}

package com.avimax.backend.repository;

import com.avimax.backend.entity.ActuatorControlState;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActuatorControlStateRepository extends JpaRepository<ActuatorControlState, Long> {
    Optional<ActuatorControlState> findByActuatorTypeAndActuatorId(String actuatorType, Long actuatorId);

    long countByActuatorType(String actuatorType);

    long countByActuatorTypeAndCurrentStateTrue(String actuatorType);

    List<ActuatorControlState> findByActuatorTypeOrderByActuatorIdAsc(String actuatorType);
}

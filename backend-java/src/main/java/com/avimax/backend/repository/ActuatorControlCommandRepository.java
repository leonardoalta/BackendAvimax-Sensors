package com.avimax.backend.repository;

import com.avimax.backend.entity.ActuatorControlCommand;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActuatorControlCommandRepository extends JpaRepository<ActuatorControlCommand, Long> {
    List<ActuatorControlCommand> findByDispatchedAtIsNullOrderByCreatedAtAsc();
}

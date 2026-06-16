package com.avimax.backend.repository;

import com.avimax.backend.entity.LocalConfigState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LocalConfigStateRepository extends JpaRepository<LocalConfigState, Long> {
    Optional<LocalConfigState> findByGatewayCode(String gatewayCode);
}

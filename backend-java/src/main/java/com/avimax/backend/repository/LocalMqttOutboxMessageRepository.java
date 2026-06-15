package com.avimax.backend.repository;

import com.avimax.backend.entity.LocalMqttOutboxMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LocalMqttOutboxMessageRepository extends JpaRepository<LocalMqttOutboxMessage, Long> {

    @Query("SELECT m FROM LocalMqttOutboxMessage m WHERE m.status IN :statuses ORDER BY m.createdAt ASC")
    List<LocalMqttOutboxMessage> findPendingForRetry(@Param("statuses") List<String> statuses, Pageable pageable);
}

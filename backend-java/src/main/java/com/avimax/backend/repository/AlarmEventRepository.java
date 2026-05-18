package com.avimax.backend.repository;

import com.avimax.backend.entity.AlarmEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlarmEventRepository extends JpaRepository<AlarmEvent, Long> {
    List<AlarmEvent> findByAlarmIdOrderByEventAtDesc(Long alarmId);
}

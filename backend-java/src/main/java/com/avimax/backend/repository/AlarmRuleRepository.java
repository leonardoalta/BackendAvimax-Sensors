package com.avimax.backend.repository;

import com.avimax.backend.entity.AlarmRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlarmRuleRepository extends JpaRepository<AlarmRule, Long> {
    List<AlarmRule> findByActiveTrueOrderByCreatedAtDesc();

    List<AlarmRule> findAllByOrderByCreatedAtDesc();
}

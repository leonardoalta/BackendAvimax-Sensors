package com.avimax.backend.repository;

import com.avimax.backend.entity.AlarmRuleState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlarmRuleStateRepository extends JpaRepository<AlarmRuleState, Long> {
    Optional<AlarmRuleState> findByRuleId(Long ruleId);
}

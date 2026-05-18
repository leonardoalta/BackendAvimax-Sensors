package com.avimax.backend.repository;

import com.avimax.backend.entity.Alarm;
import com.avimax.backend.entity.AlarmStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlarmRepository extends JpaRepository<Alarm, Long> {
    Optional<Alarm> findTopByRuleIdAndStatusInOrderByActivatedAtDesc(Long ruleId, Collection<AlarmStatus> statuses);

    List<Alarm> findByStatusInOrderByActivatedAtDesc(Collection<AlarmStatus> statuses);

    List<Alarm> findAllByOrderByActivatedAtDesc();
}

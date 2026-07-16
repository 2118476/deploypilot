package com.deploypilot.repository;

import com.deploypilot.model.AutomationRun;
import com.deploypilot.model.enums.AutomationRunStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AutomationRunRepository extends JpaRepository<AutomationRun, Long> {
    List<AutomationRun> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);
    boolean existsByProjectIdAndStatus(Long projectId, AutomationRunStatus status);
    long countByProjectId(Long projectId);
    List<AutomationRun> findByProjectIdOrderByCreatedAtAsc(Long projectId);
}

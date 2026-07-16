package com.deploypilot.repository;

import com.deploypilot.model.ProjectActivityEvent;
import com.deploypilot.model.enums.ActivityEventType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectActivityEventRepository extends JpaRepository<ProjectActivityEvent, Long> {
    List<ProjectActivityEvent> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);
    boolean existsByProjectIdAndEventType(Long projectId, ActivityEventType eventType);
    long countByProjectId(Long projectId);
    List<ProjectActivityEvent> findByProjectIdOrderByCreatedAtAsc(Long projectId);
}

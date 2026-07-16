package com.deploypilot.repository;

import com.deploypilot.model.VerificationRun;
import com.deploypilot.model.enums.VerificationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VerificationRunRepository extends JpaRepository<VerificationRun, Long> {
    List<VerificationRun> findByProjectIdOrderByStartedAtDesc(Long projectId, Pageable pageable);
    boolean existsByProjectIdAndOverallStatus(Long projectId, VerificationStatus status);
    long countByProjectId(Long projectId);
    List<VerificationRun> findByProjectIdOrderByStartedAtAsc(Long projectId);
}

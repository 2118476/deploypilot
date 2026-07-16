package com.deploypilot.repository;

import com.deploypilot.model.DeploymentTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeploymentTargetRepository extends JpaRepository<DeploymentTarget, Long> {
    List<DeploymentTarget> findByProjectIdOrderByCreatedAtAsc(Long projectId);
}

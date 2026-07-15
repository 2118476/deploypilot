package com.deploypilot.repository;

import com.deploypilot.model.DeploymentBlueprint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeploymentBlueprintRepository extends JpaRepository<DeploymentBlueprint, Long> {
    Optional<DeploymentBlueprint> findTopByProjectIdOrderByCreatedAtDesc(Long projectId);
}

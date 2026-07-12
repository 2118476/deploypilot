package com.deploypilot.repository;

import com.deploypilot.model.DeploymentPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface DeploymentPlanRepository extends JpaRepository<DeploymentPlan, Long> {
    Optional<DeploymentPlan> findByProjectId(Long projectId);
}

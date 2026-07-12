package com.deploypilot.repository;

import com.deploypilot.model.StepProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface StepProgressRepository extends JpaRepository<StepProgress, Long> {
    List<StepProgress> findByDeploymentPlanId(Long deploymentPlanId);
    Optional<StepProgress> findByDeploymentPlanIdAndStepIndex(Long planId, int stepIndex);
}

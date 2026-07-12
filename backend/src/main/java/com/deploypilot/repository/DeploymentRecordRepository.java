package com.deploypilot.repository;

import com.deploypilot.model.DeploymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DeploymentRecordRepository extends JpaRepository<DeploymentRecord, Long> {
    List<DeploymentRecord> findByProjectIdOrderByDeployedAtDesc(Long projectId);
}

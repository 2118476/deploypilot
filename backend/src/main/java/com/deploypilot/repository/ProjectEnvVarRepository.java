package com.deploypilot.repository;

import com.deploypilot.model.ProjectEnvVar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProjectEnvVarRepository extends JpaRepository<ProjectEnvVar, Long> {
    List<ProjectEnvVar> findByProjectId(Long projectId);
}

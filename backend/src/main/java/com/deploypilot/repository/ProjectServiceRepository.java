package com.deploypilot.repository;

import com.deploypilot.model.ProjectService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProjectServiceRepository extends JpaRepository<ProjectService, Long> {
    List<ProjectService> findByProjectId(Long projectId);
}

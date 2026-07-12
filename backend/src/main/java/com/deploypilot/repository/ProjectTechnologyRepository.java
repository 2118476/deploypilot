package com.deploypilot.repository;

import com.deploypilot.model.ProjectTechnology;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProjectTechnologyRepository extends JpaRepository<ProjectTechnology, Long> {
    List<ProjectTechnology> findByProjectId(Long projectId);
    void deleteByProjectId(Long projectId);
}

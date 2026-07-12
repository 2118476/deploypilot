package com.deploypilot.repository;

import com.deploypilot.model.SecurityCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SecurityCheckRepository extends JpaRepository<SecurityCheck, Long> {
    List<SecurityCheck> findByProjectId(Long projectId);
}

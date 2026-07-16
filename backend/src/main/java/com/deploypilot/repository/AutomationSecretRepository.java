package com.deploypilot.repository;

import com.deploypilot.model.AutomationSecret;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AutomationSecretRepository extends JpaRepository<AutomationSecret, Long> {
    List<AutomationSecret> findByProjectIdOrderByVarNameAsc(Long projectId);
    Optional<AutomationSecret> findByProjectIdAndVarName(Long projectId, String varName);
    void deleteByProjectIdAndVarName(Long projectId, String varName);
}

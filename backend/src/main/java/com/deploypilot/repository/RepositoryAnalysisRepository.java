package com.deploypilot.repository;

import com.deploypilot.model.RepositoryAnalysis;
import com.deploypilot.model.enums.AnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RepositoryAnalysisRepository extends JpaRepository<RepositoryAnalysis, Long> {
    Optional<RepositoryAnalysis> findTopByProjectIdOrderByCreatedAtDesc(Long projectId);
    Optional<RepositoryAnalysis> findTopByProjectIdAndStatusOrderByCreatedAtDesc(Long projectId, AnalysisStatus status);
}

package com.deploypilot.repository;

import com.deploypilot.model.RepositoryAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RepositoryAnalysisRepository extends JpaRepository<RepositoryAnalysis, Long> {
    Optional<RepositoryAnalysis> findTopByProjectIdOrderByCreatedAtDesc(Long projectId);
}

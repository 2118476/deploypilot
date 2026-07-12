package com.deploypilot.repository;

import com.deploypilot.model.EnvVarDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface EnvVarDefinitionRepository extends JpaRepository<EnvVarDefinition, Long> {
    Optional<EnvVarDefinition> findByName(String name);
}

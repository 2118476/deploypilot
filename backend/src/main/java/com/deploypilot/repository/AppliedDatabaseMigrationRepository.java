package com.deploypilot.repository;

import com.deploypilot.model.AppliedDatabaseMigration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppliedDatabaseMigrationRepository extends JpaRepository<AppliedDatabaseMigration, Long> {
    List<AppliedDatabaseMigration> findByProjectIdAndSupabaseProjectRef(Long projectId, String supabaseProjectRef);
    Optional<AppliedDatabaseMigration> findByProjectIdAndSupabaseProjectRefAndMigrationName(
        Long projectId, String supabaseProjectRef, String migrationName);
}

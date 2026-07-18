package com.deploypilot.service;

import com.deploypilot.dto.DeploymentActionPlan.DatabaseHandoff;
import com.deploypilot.model.AppliedDatabaseMigration;
import com.deploypilot.model.enums.ProviderType;
import com.deploypilot.provider.DatabaseProvider;
import com.deploypilot.provider.ProviderCredential;
import com.deploypilot.provider.ProviderException;
import com.deploypilot.provider.ProviderRegistry;
import com.deploypilot.provider.model.*;
import com.deploypilot.repoaccess.RepositoryFileReader;
import com.deploypilot.repoaccess.RepositoryFileReaderFactory;
import com.deploypilot.repoaccess.RepositoryRef;
import com.deploypilot.repository.AppliedDatabaseMigrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the Supabase execution collaborator: idempotent project creation
 * and retry-reuse, free-plan-only, destructive migrations skipped, checksum
 * de-duplication, and correct backend/frontend secret routing (tests 10–16).
 */
class SupabaseCollaboratorTest {

    private DatabaseProvider db;
    private SecretService secretService;
    private MigrationDiscoveryService migrationDiscovery;
    private AppliedDatabaseMigrationRepository appliedRepo;
    private SupabaseDeploymentCollaborator collaborator;

    private final ProviderCredential cred = new ProviderCredential(ProviderType.SUPABASE,
        com.deploypilot.model.enums.ConnectionType.SUPABASE_PAT, "sbp_token");

    @BeforeEach
    void setup() {
        ProviderRegistry providers = mock(ProviderRegistry.class);
        db = mock(DatabaseProvider.class);
        when(providers.database(ProviderType.SUPABASE)).thenReturn(db);
        secretService = mock(SecretService.class);
        migrationDiscovery = mock(MigrationDiscoveryService.class);
        appliedRepo = mock(AppliedDatabaseMigrationRepository.class);
        ConnectionService connectionService = mock(ConnectionService.class);
        when(connectionService.findConnection(anyLong(), any())).thenReturn(Optional.empty());
        RepositoryFileReaderFactory readerFactory = mock(RepositoryFileReaderFactory.class);
        when(readerFactory.forCredentialOrDefault(any())).thenReturn(mock(RepositoryFileReader.class));
        collaborator = new SupabaseDeploymentCollaborator(providers, secretService, migrationDiscovery, appliedRepo,
            connectionService, readerFactory, 5, 3);
    }

    private DatabaseHandoff createHandoff() {
        DatabaseHandoff h = new DatabaseHandoff();
        h.setChoice("CREATE_SUPABASE_PROJECT");
        h.setSupabaseOrgId("org-1");
        h.setSupabaseProjectName("my-db");
        h.setSupabaseRegion("us-east-1");
        return h;
    }

    @Test
    void createIsIdempotentAndUsesFreePlan() {
        when(secretService.getOrGenerate(anyLong(), anyLong(), eq("SUPABASE_DB_PASSWORD"))).thenReturn("gen-pw");
        ArgumentCaptor<DatabaseProjectRequest> reqCap = ArgumentCaptor.forClass(DatabaseProjectRequest.class);
        when(db.createProject(any(), reqCap.capture())).thenReturn(
            new DatabaseProject("proj-1", "my-db", "org-1", "us-east-1", DatabaseStatus.ACTIVE_HEALTHY, "db.proj-1.supabase.co", "https://proj-1.supabase.co"));

        Map<String, String> outputs = new HashMap<>();
        collaborator.create(cred, createHandoff(), 1L, 1L, outputs);
        assertEquals("proj-1", outputs.get("supabaseProjectRef"));
        assertEquals("free", reqCap.getValue().plan(), "must request the free plan");

        // Second call with the ref already captured (retry) must NOT create again.
        collaborator.create(cred, createHandoff(), 1L, 1L, outputs);
        verify(db, times(1)).createProject(any(), any());
    }

    @Test
    void billingRequiredPropagates() {
        when(secretService.getOrGenerate(anyLong(), anyLong(), any())).thenReturn("pw");
        when(db.createProject(any(), any())).thenThrow(new ProviderException.BillingRequired("free tier unavailable"));
        assertThrows(ProviderException.BillingRequired.class,
            () -> collaborator.create(cred, createHandoff(), 1L, 1L, new HashMap<>()));
    }

    @Test
    void destructiveMigrationsAreNeverApplied() {
        Map<String, String> outputs = new HashMap<>();
        outputs.put("supabaseProjectRef", "proj-1");
        when(migrationDiscovery.discover(any(), any(), anyLong(), any(), any())).thenReturn(List.of(
            new MigrationInfo("001_drop.sql", "supabase/migrations/001_drop.sql", "chk1", 1, false, true,
                MigrationInfo.POTENTIALLY_DESTRUCTIVE, "Contains DROP TABLE")));

        collaborator.migrationsApply(cred, RepositoryRef.parse("demo/repo"), "main", 1L, 1L, outputs);
        verify(db, never()).applyMigration(any(), any(), any(), any());
    }

    @Test
    void checksumPreventsReapplyingCompletedMigrations() {
        Map<String, String> outputs = new HashMap<>();
        outputs.put("supabaseProjectRef", "proj-1");
        MigrationInfo m = new MigrationInfo("001_init.sql", "supabase/migrations/001_init.sql", "chkA", 1, false, false,
            MigrationInfo.SAFE, null);
        when(migrationDiscovery.discover(any(), any(), anyLong(), any(), any())).thenReturn(List.of(m));

        // Already applied with the SAME checksum -> skipped.
        AppliedDatabaseMigration applied = new AppliedDatabaseMigration();
        applied.setChecksum("chkA");
        when(appliedRepo.findByProjectIdAndSupabaseProjectRefAndMigrationName(1L, "proj-1", "001_init.sql"))
            .thenReturn(Optional.of(applied));
        collaborator.migrationsApply(cred, RepositoryRef.parse("demo/repo"), "main", 1L, 1L, outputs);
        verify(db, never()).applyMigration(any(), any(), any(), any());

        // Not applied yet -> applied once and recorded.
        when(appliedRepo.findByProjectIdAndSupabaseProjectRefAndMigrationName(1L, "proj-1", "001_init.sql"))
            .thenReturn(Optional.empty());
        when(migrationDiscovery.readMigrationSql(any(), any(), any(), any())).thenReturn("CREATE TABLE t (id int);");
        when(db.applyMigration(any(), eq("proj-1"), eq("001_init.sql"), any()))
            .thenReturn(new MigrationResult("001_init.sql", true, "Applied."));
        collaborator.migrationsApply(cred, RepositoryRef.parse("demo/repo"), "main", 1L, 1L, outputs);
        verify(db, times(1)).applyMigration(any(), eq("proj-1"), eq("001_init.sql"), any());
        verify(appliedRepo, atLeastOnce()).save(any());
    }

    @Test
    void credentialsRouteSecretsSafelyAndNeverLeakToOutputs() {
        Map<String, String> outputs = new HashMap<>();
        outputs.put("supabaseProjectRef", "proj-1");
        when(secretService.getValue(1L, "SUPABASE_DB_PASSWORD")).thenReturn(Optional.of("db-pass-123"));
        when(db.getConnectionInfo(any(), eq("proj-1"), any())).thenReturn(new DatabaseConnectionInfo(
            "db.proj-1.supabase.co", 5432, "postgres", "postgres", "db-pass-123",
            "jdbc:postgresql://db.proj-1.supabase.co:5432/postgres", "https://proj-1.supabase.co",
            "anon-public", "service-role-SECRET"));

        collaborator.credentials(cred, 1L, 1L, outputs);

        // Backend-only secrets stored to the backend.
        verify(secretService).store(1L, 1L, "DATABASE_URL", "postgresql://postgres:db-pass-123@db.proj-1.supabase.co:5432/postgres", "Backend service");
        verify(secretService).store(1L, 1L, "SUPABASE_SERVICE_ROLE_KEY", "service-role-SECRET", "Backend service");
        // Public values to the frontend.
        verify(secretService).store(1L, 1L, "VITE_SUPABASE_URL", "https://proj-1.supabase.co", "Frontend site");
        verify(secretService).store(1L, 1L, "VITE_SUPABASE_ANON_KEY", "anon-public", "Frontend site");

        // Outputs never carry the password or service-role key.
        String serialised = outputs.toString();
        assertFalse(serialised.contains("db-pass-123"));
        assertFalse(serialised.contains("service-role-SECRET"));
        assertEquals("https://proj-1.supabase.co", outputs.get("supabaseProjectUrl"));
    }
}

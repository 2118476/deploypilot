package com.deploypilot.service;

import com.deploypilot.dto.DeploymentActionPlan.DatabaseHandoff;
import com.deploypilot.migration.MigrationSafety;
import com.deploypilot.model.AppliedDatabaseMigration;
import com.deploypilot.model.enums.ProviderType;
import com.deploypilot.provider.ProviderCredential;
import com.deploypilot.provider.ProviderException;
import com.deploypilot.provider.ProviderRegistry;
import com.deploypilot.provider.model.*;
import com.deploypilot.repoaccess.RepositoryRef;
import com.deploypilot.repository.AppliedDatabaseMigrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Executes the Supabase database steps for the automation engine. Kept separate
 * from {@link DeploymentExecutor} to keep the executor focused. It is idempotent
 * (reuses a created project, never reapplies a migration with a matching
 * checksum), never creates a paid project, and never writes a database password
 * or service-role key into run outputs, logs or AI context.
 */
@Service
public class SupabaseDeploymentCollaborator {

    private static final Logger log = LoggerFactory.getLogger(SupabaseDeploymentCollaborator.class);

    static final String OUT_SUPABASE_REF = "supabaseProjectRef";
    static final String OUT_SUPABASE_URL = "supabaseProjectUrl";
    static final String OUT_MIGRATION_STATUS = "databaseMigrationStatus";
    static final String DB_PASSWORD_SECRET = "SUPABASE_DB_PASSWORD";
    static final int MAX_APPLY = 50;

    private final ProviderRegistry providers;
    private final SecretService secretService;
    private final MigrationDiscoveryService migrationDiscovery;
    private final AppliedDatabaseMigrationRepository appliedRepository;
    private final ConnectionService connectionService;
    private final com.deploypilot.repoaccess.RepositoryFileReaderFactory readerFactory;
    private final SecureRandom random = new SecureRandom();
    private final long pollIntervalMs;
    private final int pollMaxAttempts;

    public SupabaseDeploymentCollaborator(ProviderRegistry providers,
                                          SecretService secretService,
                                          MigrationDiscoveryService migrationDiscovery,
                                          AppliedDatabaseMigrationRepository appliedRepository,
                                          ConnectionService connectionService,
                                          com.deploypilot.repoaccess.RepositoryFileReaderFactory readerFactory,
                                          @Value("${deploypilot.automation.poll-interval-ms:2000}") long pollIntervalMs,
                                          @Value("${deploypilot.automation.poll-max-attempts:60}") int pollMaxAttempts) {
        this.providers = providers;
        this.secretService = secretService;
        this.migrationDiscovery = migrationDiscovery;
        this.appliedRepository = appliedRepository;
        this.connectionService = connectionService;
        this.readerFactory = readerFactory;
        this.pollIntervalMs = pollIntervalMs;
        this.pollMaxAttempts = pollMaxAttempts;
    }

    /** A repository reader scoped to the user's connected GitHub credential (server token fallback). */
    private com.deploypilot.repoaccess.RepositoryFileReader gitHubReader(Long userId) {
        ProviderCredential cred = null;
        if (userId != null && connectionService.findConnection(userId, ProviderType.GITHUB).isPresent()) {
            try {
                cred = connectionService.requireCredential(userId, ProviderType.GITHUB);
            } catch (Exception ignored) {
                // fall back to the server-level token
            }
        }
        return readerFactory.forCredentialOrDefault(cred);
    }

    /** Reuse an existing selected project (read-only). */
    public String inspect(ProviderCredential cred, DatabaseHandoff db, Map<String, String> outputs) {
        String ref = db.getSupabaseProjectRef();
        DatabaseProject project = provider().getProject(cred, ref);
        outputs.put(OUT_SUPABASE_REF, project.ref());
        if (project.restUrl() != null) outputs.put(OUT_SUPABASE_URL, project.restUrl());
        return "Using existing Supabase project " + project.name() + " (status " + project.status() + ").";
    }

    /** Create a project on the free plan. Idempotent: reuse a ref already captured on retry. */
    public String create(ProviderCredential cred, DatabaseHandoff db, Long projectId, Long userId, Map<String, String> outputs) {
        if (outputs.containsKey(OUT_SUPABASE_REF)) {
            return "Reusing the Supabase project created earlier (" + outputs.get(OUT_SUPABASE_REF) + ").";
        }
        String password = secretService.getOrGenerate(projectId, userId, DB_PASSWORD_SECRET);
        String region = notBlank(db.getSupabaseRegion()) ? db.getSupabaseRegion() : "us-east-1";
        DatabaseProjectRequest req = new DatabaseProjectRequest(db.getSupabaseOrgId(),
            db.getSupabaseProjectName(), region, DatabaseProjectRequest.FREE_PLAN, password);
        DatabaseProject project = provider().createProject(cred, req); // BillingRequired propagates -> executor pauses
        outputs.put(OUT_SUPABASE_REF, project.ref());
        if (project.restUrl() != null) outputs.put(OUT_SUPABASE_URL, project.restUrl());
        return "Created Supabase project " + project.name() + " on the free plan.";
    }

    /** Poll until the project is active and healthy. */
    public String waitReady(ProviderCredential cred, Map<String, String> outputs) {
        String ref = requireRef(outputs);
        DatabaseStatus status = provider().getStatus(cred, ref);
        for (int attempt = 0; attempt < pollMaxAttempts && !status.isReady(); attempt++) {
            if (status.isTerminalFailure()) {
                throw new ProviderException.UnexpectedResult("The Supabase project failed to provision (status " + status + ").");
            }
            sleep(pollIntervalMs);
            status = provider().getStatus(cred, ref);
        }
        if (!status.isReady()) {
            throw new ProviderException.UnexpectedResult("The Supabase project did not become ready in time. Retry shortly.");
        }
        return "Supabase project is active and healthy.";
    }

    public String migrationsInspect(RepositoryRef repoRef, String branch, DatabaseHandoff db, Long projectId,
                                    Long userId, Map<String, String> outputs) {
        List<MigrationInfo> migrations = migrationDiscovery.discover(repoRef, branch, projectId,
            outputs.get(OUT_SUPABASE_REF), gitHubReader(userId));
        long destructive = migrations.stream().filter(MigrationInfo::destructive).count();
        outputs.put(OUT_MIGRATION_STATUS, migrations.size() + " reviewed");
        return "Reviewed " + migrations.size() + " repository migration(s)"
            + (destructive > 0 ? "; " + destructive + " flagged as potentially destructive and will not be applied." : ".");
    }

    /** Apply only safe, not-previously-applied migrations, recording checksums. */
    public String migrationsApply(ProviderCredential cred, RepositoryRef repoRef, String branch,
                                  Long projectId, Long userId, Map<String, String> outputs) {
        String ref = requireRef(outputs);
        com.deploypilot.repoaccess.RepositoryFileReader reader = gitHubReader(userId);
        List<MigrationInfo> migrations = migrationDiscovery.discover(repoRef, branch, projectId, ref, reader);
        int applied = 0, skipped = 0, count = 0;
        for (MigrationInfo m : migrations) {
            if (count++ >= MAX_APPLY) break;
            if (m.destructive()) {
                // A safety net: destructive migrations are never applied automatically.
                skipped++;
                continue;
            }
            Optional<AppliedDatabaseMigration> already = appliedRepository
                .findByProjectIdAndSupabaseProjectRefAndMigrationName(projectId, ref, m.name());
            if (already.isPresent() && already.get().getChecksum().equals(m.checksum())) {
                skipped++;
                continue; // checksum matches -> do not reapply
            }
            String sql = migrationDiscovery.readMigrationSql(repoRef, branch, m.path(), reader);
            // Re-verify safety on the exact SQL we are about to run.
            if (MigrationSafety.detect(sql).destructive()) {
                skipped++;
                continue;
            }
            MigrationResult result = provider().applyMigration(cred, ref, m.name(), sql);
            if (!result.applied()) {
                throw new ProviderException.UnexpectedResult("Migration " + m.name() + " failed: " + result.message());
            }
            record(userId, projectId, ref, m.name(), m.checksum());
            applied++;
        }
        outputs.put(OUT_MIGRATION_STATUS, "applied " + applied + ", skipped " + skipped);
        return "Applied " + applied + " migration(s); skipped " + skipped + " (already applied or not safe).";
    }

    /**
     * Fetch connection details and prepare backend/frontend variables as encrypted
     * secrets. Backend-only values (DATABASE_URL, JDBC URL, service-role key,
     * password) never reach the frontend; only the public URL and anon key do.
     */
    public String credentials(ProviderCredential cred, Long projectId, Long userId, Map<String, String> outputs) {
        String ref = requireRef(outputs);
        String password = secretService.getValue(projectId, DB_PASSWORD_SECRET)
            .or(() -> secretService.getValue(projectId, "DATABASE_PASSWORD")).orElse(null);
        DatabaseConnectionInfo info = provider().getConnectionInfo(cred, ref, password == null ? "" : password);

        // Backend-only secrets.
        if (password != null && info.host() != null) {
            String databaseUrl = "postgresql://postgres:" + password + "@" + info.host() + ":5432/postgres";
            secretService.store(projectId, userId, "DATABASE_URL", databaseUrl, "Backend service");
            secretService.store(projectId, userId, "JDBC_DATABASE_URL", info.jdbcUrl(), "Backend service");
            secretService.store(projectId, userId, "DATABASE_PASSWORD", password, "Backend service");
        }
        if (info.serviceRoleKey() != null) {
            secretService.store(projectId, userId, "SUPABASE_SERVICE_ROLE_KEY", info.serviceRoleKey(), "Backend service");
        }
        // Backend also gets the project URL (same value as the public URL, but the
        // backend references it as SUPABASE_URL rather than the VITE_ variant).
        if (info.restUrl() != null) secretService.store(projectId, userId, "SUPABASE_URL", info.restUrl(), "Backend service");
        // Frontend-safe (public) values.
        if (info.restUrl() != null) secretService.store(projectId, userId, "VITE_SUPABASE_URL", info.restUrl(), "Frontend site");
        if (info.anonKey() != null) secretService.store(projectId, userId, "VITE_SUPABASE_ANON_KEY", info.anonKey(), "Frontend site");

        if (info.restUrl() != null) outputs.put(OUT_SUPABASE_URL, info.restUrl());
        return "Prepared database connection variables (backend secrets stored encrypted; only the public URL and anon "
            + "key are frontend-safe).";
    }

    public String verifyDatabase(ProviderCredential cred, Map<String, String> outputs) {
        String ref = outputs.get(OUT_SUPABASE_REF);
        if (ref == null) return "No Supabase project to verify.";
        DatabaseStatus status = provider().getStatus(cred, ref);
        return "Supabase project status: " + status + (status.isReady() ? " (healthy)." : ".");
    }

    // ---------- internals ----------

    private com.deploypilot.provider.DatabaseProvider provider() {
        return providers.database(ProviderType.SUPABASE);
    }

    private void record(Long userId, Long projectId, String ref, String name, String checksum) {
        AppliedDatabaseMigration a = appliedRepository
            .findByProjectIdAndSupabaseProjectRefAndMigrationName(projectId, ref, name)
            .orElseGet(AppliedDatabaseMigration::new);
        a.setUserId(userId);
        a.setProjectId(projectId);
        a.setSupabaseProjectRef(ref);
        a.setMigrationName(name);
        a.setChecksum(checksum);
        appliedRepository.save(a);
    }

    private String requireRef(Map<String, String> outputs) {
        String ref = outputs.get(OUT_SUPABASE_REF);
        if (ref == null) throw new ProviderException.UnexpectedResult("No Supabase project reference was captured.");
        return ref;
    }

    private boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

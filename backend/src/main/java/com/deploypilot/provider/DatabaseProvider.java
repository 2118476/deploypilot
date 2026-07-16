package com.deploypilot.provider;

import com.deploypilot.model.enums.ProviderType;
import com.deploypilot.provider.model.DatabaseConnectionInfo;
import com.deploypilot.provider.model.DatabaseOrganization;
import com.deploypilot.provider.model.DatabaseProject;
import com.deploypilot.provider.model.DatabaseProjectRequest;
import com.deploypilot.provider.model.DatabaseStatus;
import com.deploypilot.provider.model.MigrationResult;
import com.deploypilot.provider.model.ProviderAccount;

import java.util.List;

/**
 * Operations against a database/platform provider (Supabase). A database
 * provider is deliberately distinct from a {@code HostingProvider}. It never
 * deletes projects and never selects a paid plan; when free creation is
 * unavailable it raises {@link ProviderException.BillingRequired} rather than
 * creating a paid resource.
 */
public interface DatabaseProvider {

    ProviderType type();

    /** Validates the credential and returns the connected account's identity. */
    ProviderAccount getAccount(ProviderCredential credential);

    /** Organizations the credential can access. */
    List<DatabaseOrganization> listOrganizations(ProviderCredential credential);

    /** Existing projects on the account (bounded). */
    List<DatabaseProject> listProjects(ProviderCredential credential);

    /** A single project by ref, or throws {@link ProviderException.NotFound}. */
    DatabaseProject getProject(ProviderCredential credential, String ref);

    /** Normalised status of a project. */
    DatabaseStatus getStatus(ProviderCredential credential, String ref);

    /**
     * Creates a new project on the free plan. Callers must first check
     * {@link #listProjects} to avoid duplicates. Raises
     * {@link ProviderException.BillingRequired} if the free tier is unavailable.
     */
    DatabaseProject createProject(ProviderCredential credential, DatabaseProjectRequest request);

    /**
     * Assembles connection details (host, keys, connection strings). The supplied
     * {@code dbPassword} is embedded in the returned connection strings; the
     * result is never persisted in run outputs or logs.
     */
    DatabaseConnectionInfo getConnectionInfo(ProviderCredential credential, String ref, String dbPassword);

    /**
     * Applies a single already-vetted, non-destructive, repository-owned migration.
     * The SQL comes only from the user's repository — never from AI.
     */
    MigrationResult applyMigration(ProviderCredential credential, String ref, String migrationName, String sql);
}

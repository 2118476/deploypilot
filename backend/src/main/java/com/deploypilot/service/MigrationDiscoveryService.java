package com.deploypilot.service;

import com.deploypilot.migration.MigrationSafety;
import com.deploypilot.model.AppliedDatabaseMigration;
import com.deploypilot.provider.model.MigrationInfo;
import com.deploypilot.repoaccess.RepositoryFileEntry;
import com.deploypilot.repoaccess.RepositoryFileReader;
import com.deploypilot.repoaccess.RepositoryRef;
import com.deploypilot.repository.AppliedDatabaseMigrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Discovers repository-owned migrations from supported locations, computes their
 * checksums, flags potentially destructive ones and marks those already applied.
 * It reads only the user's imported repository — never DeployPilot's own source —
 * and never generates SQL.
 */
@Service
public class MigrationDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(MigrationDiscoveryService.class);

    static final int MAX_MIGRATION_FILES = 50;
    static final int MAX_MIGRATION_BYTES = 200 * 1024;

    private static final String[] MIGRATION_DIRS = {
        "supabase/migrations/", "database/migrations/", "db/migration/"
        // "db/migration/" also matches backend/src/main/resources/db/migration/
    };

    // Single-file schema/setup scripts (e.g. JobPilot's supabase/schema.sql) that
    // are database setup even though they are not in a migrations directory.
    private static final String[] SCHEMA_FILES = {
        "supabase/schema.sql", "supabase/seed.sql", "database/schema.sql",
        "db/schema.sql", "schema.sql", "supabase/setup.sql"
    };

    private final RepositoryFileReader fileReader;
    private final AppliedDatabaseMigrationRepository appliedRepository;

    public MigrationDiscoveryService(RepositoryFileReader fileReader,
                                     AppliedDatabaseMigrationRepository appliedRepository) {
        this.fileReader = fileReader;
        this.appliedRepository = appliedRepository;
    }

    /** Deterministically-ordered migration metadata for the repository. */
    public List<MigrationInfo> discover(RepositoryRef ref, String branch, Long projectId, String supabaseProjectRef) {
        List<RepositoryFileEntry> all;
        try {
            all = fileReader.listFiles(ref, branch).entries();
        } catch (Exception e) {
            log.debug("Migration discovery could not list files: {}", e.getMessage());
            return List.of();
        }
        List<String> paths = new ArrayList<>();
        for (RepositoryFileEntry e : all) {
            if (isMigrationPath(e.path())) paths.add(e.path());
        }
        paths.sort(String::compareTo); // deterministic order

        List<MigrationInfo> out = new ArrayList<>();
        int order = 0;
        for (String path : paths) {
            if (order >= MAX_MIGRATION_FILES) break;
            String sql;
            try {
                sql = fileReader.readTextFile(ref, branch, path, MAX_MIGRATION_BYTES);
            } catch (Exception e) {
                log.debug("Could not read migration {}: {}", path, e.getMessage());
                continue;
            }
            order++;
            String name = fileName(path);
            String checksum = MigrationSafety.checksum(sql);
            MigrationSafety.Verdict verdict = MigrationSafety.detect(sql);
            boolean applied = isPreviouslyApplied(projectId, supabaseProjectRef, name, checksum);
            out.add(new MigrationInfo(name, path, checksum, order, applied, verdict.destructive(),
                verdict.destructive() ? MigrationInfo.POTENTIALLY_DESTRUCTIVE : MigrationInfo.SAFE, verdict.reason()));
        }
        return out;
    }

    /** Reads the SQL for one migration file at apply time (bounded). */
    public String readMigrationSql(RepositoryRef ref, String branch, String path) {
        return fileReader.readTextFile(ref, branch, path, MAX_MIGRATION_BYTES);
    }

    private boolean isPreviouslyApplied(Long projectId, String supabaseProjectRef, String name, String checksum) {
        if (supabaseProjectRef == null) return false;
        Optional<AppliedDatabaseMigration> applied = appliedRepository
            .findByProjectIdAndSupabaseProjectRefAndMigrationName(projectId, supabaseProjectRef, name);
        return applied.map(a -> a.getChecksum().equals(checksum)).orElse(false);
    }

    private boolean isMigrationPath(String path) {
        if (path == null || !path.toLowerCase().endsWith(".sql")) return false;
        String p = path.replace('\\', '/');
        String lower = p.toLowerCase();
        for (String dir : MIGRATION_DIRS) {
            if (lower.contains(dir)) return true;
        }
        // Single-file schema/setup scripts anywhere in the tree.
        for (String schema : SCHEMA_FILES) {
            if (lower.equals(schema) || lower.endsWith("/" + schema)) return true;
        }
        return false;
    }

    private String fileName(String path) {
        String p = path.replace('\\', '/');
        int slash = p.lastIndexOf('/');
        return slash >= 0 ? p.substring(slash + 1) : p;
    }
}

package com.deploypilot.repoaccess;

import java.util.List;

/**
 * Read-only access to a source repository. Implementations must never
 * modify the repository in any way.
 */
public interface RepositoryFileReader {

    /** Fetch repository metadata (existence check, default branch, visibility). */
    RepositoryMetadata fetchMetadata(RepositoryRef ref);

    /**
     * List all files (blobs, not directories) in the repository at the given branch.
     * Returns entries with paths relative to the repository root.
     */
    FileListing listFiles(RepositoryRef ref, String branch);

    /**
     * Read a text file's content. Callers are expected to have filtered by size
     * beforehand; implementations should still refuse to return more than maxBytes.
     */
    String readTextFile(RepositoryRef ref, String branch, String path, int maxBytes);

    /** File listing plus a flag for providers that truncate large trees. */
    record FileListing(List<RepositoryFileEntry> entries, boolean truncated) {}
}

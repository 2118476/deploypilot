package com.deploypilot.provider;

import com.deploypilot.provider.model.CommitFile;
import com.deploypilot.provider.model.ProviderAccount;
import com.deploypilot.provider.model.PullRequestResult;
import com.deploypilot.provider.model.RepoCommit;
import com.deploypilot.provider.model.RepositorySummary;
import com.deploypilot.repoaccess.RepositoryRef;

import java.util.List;

/**
 * Read/prepare operations against a git host (GitHub). Write operations are
 * limited to creating a dedicated branch and opening a pull request — this
 * abstraction never commits to the default branch and never force-pushes.
 */
public interface GitProvider {

    /** Validates the credential and returns the connected account's identity. */
    ProviderAccount getAccount(ProviderCredential credential);

    /** Repositories the credential can access (bounded). */
    List<RepositorySummary> listRepositories(ProviderCredential credential);

    /** Metadata for a single repository. */
    RepositorySummary getRepository(ProviderCredential credential, RepositoryRef ref);

    /** The tip commit of the given branch. */
    RepoCommit getLatestCommit(ProviderCredential credential, RepositoryRef ref, String branch);

    /** Returns the file's text content, or null if it does not exist. */
    String getFileContentOrNull(ProviderCredential credential, RepositoryRef ref, String branch, String path);

    /**
     * Creates a dedicated branch from {@code baseBranch}, commits the given files
     * and opens a pull request. Idempotent: if the branch already exists with the
     * same content, or no files differ, no duplicate PR is opened. Never writes to
     * the base branch directly.
     */
    PullRequestResult openConfigPullRequest(ProviderCredential credential, RepositoryRef ref,
                                            String baseBranch, String newBranch, String title,
                                            String body, List<CommitFile> files);
}

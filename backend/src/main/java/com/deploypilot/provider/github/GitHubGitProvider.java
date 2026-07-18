package com.deploypilot.provider.github;

import com.deploypilot.provider.GitProvider;
import com.deploypilot.provider.ProviderApiClient;
import com.deploypilot.provider.ProviderApiClient.ApiResult;
import com.deploypilot.provider.ProviderCredential;
import com.deploypilot.provider.ProviderException;
import com.deploypilot.provider.ProviderProperties;
import com.deploypilot.provider.model.CommitFile;
import com.deploypilot.provider.model.ProviderAccount;
import com.deploypilot.provider.model.PullRequestResult;
import com.deploypilot.provider.model.RepoCommit;
import com.deploypilot.provider.model.RepositorySummary;
import com.deploypilot.repoaccess.RepositoryRef;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GitHub adapter. Read operations plus a strictly limited write path: create a
 * dedicated branch, commit generated configuration files and open a pull
 * request. It never commits to or force-pushes the default branch.
 */
@Component
public class GitHubGitProvider implements GitProvider {

    private static final int MAX_REPOS = 100;
    private static final String RAW_ACCEPT = "application/vnd.github.raw+json";

    private final ProviderApiClient http;
    private final String baseUrl;

    public GitHubGitProvider(ProviderApiClient http, ProviderProperties properties) {
        this.http = http;
        this.baseUrl = properties.gitHubBaseUrl();
    }

    @Override
    public ProviderAccount getAccount(ProviderCredential credential) {
        ApiResult r = http.get(baseUrl + "/user", credential);
        if (r.isUnauthorized()) {
            throw new ProviderException.BadCredentials("GitHub rejected the token. Check it has not expired.");
        }
        if (!r.isSuccess()) {
            throw new ProviderException.UnexpectedResult("GitHub returned status " + r.status() + " for the account.");
        }
        String login = r.body().path("login").asText(null);
        String id = r.body().path("id").asText(null);
        return new ProviderAccount(id, login, "Repository contents (read), pull requests (write)");
    }

    @Override
    public List<RepositorySummary> listRepositories(ProviderCredential credential) {
        ApiResult r = http.get(baseUrl + "/user/repos?per_page=" + MAX_REPOS + "&sort=updated&affiliation=owner,collaborator,organization_member", credential);
        if (r.isUnauthorized()) {
            throw new ProviderException.BadCredentials("GitHub rejected the token.");
        }
        if (!r.isSuccess() || !r.body().isArray()) {
            throw new ProviderException.UnexpectedResult("Could not list GitHub repositories (status " + r.status() + ").");
        }
        List<RepositorySummary> repos = new ArrayList<>();
        for (JsonNode node : r.body()) {
            repos.add(new RepositorySummary(
                node.path("full_name").asText(),
                node.path("default_branch").asText("main"),
                node.path("private").asBoolean(false),
                node.path("html_url").asText(null)));
        }
        return repos;
    }

    @Override
    public RepositorySummary getRepository(ProviderCredential credential, RepositoryRef ref) {
        ApiResult r = http.get(repo(ref), credential);
        if (r.isNotFound()) {
            throw new ProviderException.NotFound("Repository not found or not accessible: " + ref.fullName());
        }
        if (r.isUnauthorized()) {
            throw new ProviderException.BadCredentials("GitHub rejected the token for " + ref.fullName());
        }
        if (!r.isSuccess()) {
            throw new ProviderException.UnexpectedResult("GitHub returned status " + r.status() + " for " + ref.fullName());
        }
        return new RepositorySummary(
            r.body().path("full_name").asText(ref.fullName()),
            r.body().path("default_branch").asText("main"),
            r.body().path("private").asBoolean(false),
            r.body().path("html_url").asText(null));
    }

    @Override
    public RepoCommit getLatestCommit(ProviderCredential credential, RepositoryRef ref, String branch) {
        // Prefer endpoints that a fine-grained token with only "Contents" access can
        // read. The Git refs API (/git/ref/heads/{branch}) and the commits API both
        // work with Contents read/write; the /branches/{branch} endpoint additionally
        // needs Administration read for protection info and 403s for many fine-grained
        // tokens — so it is used only as a last resort.
        ApiResult gitRef = http.get(repo(ref) + "/git/ref/heads/" + enc(branch), credential);
        if (gitRef.isSuccess()) {
            String sha = gitRef.body().path("object").path("sha").asText(null);
            if (sha != null) return new RepoCommit(sha, branch);
        } else if (gitRef.isUnauthorized()) {
            throw new ProviderException.BadCredentials("GitHub rejected the token for " + ref.fullName());
        }

        ApiResult commits = http.get(repo(ref) + "/commits/" + enc(branch), credential);
        if (commits.isSuccess()) {
            String sha = commits.body().path("sha").asText(null);
            if (sha != null) return new RepoCommit(sha, branch);
        } else if (commits.isUnauthorized()) {
            throw new ProviderException.BadCredentials("GitHub rejected the token for " + ref.fullName());
        }

        ApiResult branchInfo = http.get(repo(ref) + "/branches/" + enc(branch), credential);
        if (branchInfo.isNotFound() && commits.isNotFound() && gitRef.isNotFound()) {
            throw new ProviderException.NotFound("Branch not found: " + branch + " in " + ref.fullName());
        }
        if (!branchInfo.isSuccess()) {
            throw new ProviderException.UnexpectedResult("Could not resolve the latest commit for branch "
                + branch + " (status " + branchInfo.status() + "). Ensure the GitHub token has Contents read access.");
        }
        String sha = branchInfo.body().path("commit").path("sha").asText(null);
        if (sha == null) {
            throw new ProviderException.UnexpectedResult("GitHub returned no commit sha for branch " + branch);
        }
        return new RepoCommit(sha, branch);
    }

    @Override
    public String getFileContentOrNull(ProviderCredential credential, RepositoryRef ref, String branch, String path) {
        ApiResult r = http.exchange("GET",
            repo(ref) + "/contents/" + encPath(path) + "?ref=" + enc(branch), null, credential, RAW_ACCEPT);
        if (r.isNotFound()) return null;
        if (!r.isSuccess()) {
            throw new ProviderException.UnexpectedResult("Could not read " + path + " (status " + r.status() + ").");
        }
        return r.rawBody();
    }

    @Override
    public PullRequestResult openConfigPullRequest(ProviderCredential credential, RepositoryRef ref,
                                                   String baseBranch, String newBranch, String title,
                                                   String body, List<CommitFile> files) {
        if (baseBranch != null && baseBranch.equalsIgnoreCase(newBranch)) {
            throw new ProviderException.UnexpectedResult("Refusing to commit to the base branch.");
        }
        // Skip files that already match the repository — never open a needless PR.
        List<CommitFile> changed = new ArrayList<>();
        for (CommitFile f : files) {
            String current = getFileContentOrNull(credential, ref, baseBranch, f.path());
            if (current == null || !normalise(current).equals(normalise(f.content()))) {
                changed.add(f);
            }
        }
        if (changed.isEmpty()) {
            return new PullRequestResult(null, null, null, false, "No repository changes were required.");
        }

        // Base commit + tree
        ApiResult baseRef = http.get(repo(ref) + "/git/ref/heads/" + enc(baseBranch), credential);
        if (!baseRef.isSuccess()) {
            throw new ProviderException.UnexpectedResult("Could not resolve base branch " + baseBranch + ".");
        }
        String baseSha = baseRef.body().path("object").path("sha").asText(null);
        if (baseSha == null) {
            throw new ProviderException.UnexpectedResult("GitHub returned no sha for base branch " + baseBranch + ".");
        }

        // If the branch already exists (previous run), reuse it and ensure a PR — never force-push.
        boolean branchExists = http.get(repo(ref) + "/git/ref/heads/" + enc(newBranch), credential).isSuccess();
        if (!branchExists) {
            String treeSha = createTree(credential, ref, baseSha, changed);
            String commitSha = createCommit(credential, ref, title, treeSha, baseSha);
            createBranch(credential, ref, newBranch, commitSha);
        }

        return ensurePullRequest(credential, ref, baseBranch, newBranch, title, body, !branchExists);
    }

    // ---------- internals ----------

    private String createTree(ProviderCredential credential, RepositoryRef ref, String baseSha, List<CommitFile> files) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (CommitFile f : files) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("path", f.path());
            entry.put("mode", "100644");
            entry.put("type", "blob");
            entry.put("content", f.content());
            entries.add(entry);
        }
        ApiResult r = http.post(repo(ref) + "/git/trees",
            Map.of("base_tree", baseSha, "tree", entries), credential);
        if (!r.isSuccess()) {
            throw new ProviderException.UnexpectedResult("Could not create a git tree (status " + r.status() + ").");
        }
        return r.body().path("sha").asText(null);
    }

    private String createCommit(ProviderCredential credential, RepositoryRef ref, String message, String treeSha, String parentSha) {
        ApiResult r = http.post(repo(ref) + "/git/commits",
            Map.of("message", message, "tree", treeSha, "parents", List.of(parentSha)), credential);
        if (!r.isSuccess()) {
            throw new ProviderException.UnexpectedResult("Could not create a commit (status " + r.status() + ").");
        }
        return r.body().path("sha").asText(null);
    }

    private void createBranch(ProviderCredential credential, RepositoryRef ref, String branch, String sha) {
        ApiResult r = http.post(repo(ref) + "/git/refs",
            Map.of("ref", "refs/heads/" + branch, "sha", sha), credential);
        // 422 means the ref already exists — acceptable (idempotent); anything else is a hard error.
        if (!r.isSuccess() && r.status() != 422) {
            throw new ProviderException.UnexpectedResult("Could not create branch " + branch + " (status " + r.status() + ").");
        }
    }

    private PullRequestResult ensurePullRequest(ProviderCredential credential, RepositoryRef ref, String base,
                                                String head, String title, String body, boolean committed) {
        ApiResult r = http.post(repo(ref) + "/pulls",
            Map.of("title", title, "head", head, "base", base, "body", body == null ? "" : body), credential);
        if (r.isSuccess()) {
            return new PullRequestResult(head, r.body().path("html_url").asText(null),
                r.body().path("number").isInt() ? r.body().path("number").asInt() : null, true,
                committed ? "Opened a pull request with the generated configuration." : "Reused existing branch and opened a pull request.");
        }
        // A PR for this head may already be open — reuse it rather than failing.
        ApiResult existing = http.get(repo(ref) + "/pulls?state=open&head="
            + enc(ref.owner() + ":" + head), credential);
        if (existing.isSuccess() && existing.body().isArray() && existing.body().size() > 0) {
            JsonNode pr = existing.body().get(0);
            return new PullRequestResult(head, pr.path("html_url").asText(null),
                pr.path("number").isInt() ? pr.path("number").asInt() : null, false,
                "A pull request for this branch already exists.");
        }
        throw new ProviderException.UnexpectedResult("Could not open a pull request (status " + r.status() + ").");
    }

    private String repo(RepositoryRef ref) {
        return baseUrl + "/repos/" + ref.owner() + "/" + ref.name();
    }

    private static String enc(String s) {
        return UriUtils.encodeQueryParam(s, StandardCharsets.UTF_8);
    }

    private static String encPath(String s) {
        return UriUtils.encodePath(s, StandardCharsets.UTF_8);
    }

    /** Ignore trailing-whitespace/newline noise when deciding whether a file changed. */
    private static String normalise(String s) {
        return s == null ? "" : s.strip().replace("\r\n", "\n");
    }
}

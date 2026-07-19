package com.deploypilot.automation;

import com.deploypilot.model.enums.ConnectionType;
import com.deploypilot.model.enums.ProviderType;
import com.deploypilot.provider.ProviderApiClient;
import com.deploypilot.provider.ProviderCredential;
import com.deploypilot.provider.ProviderProperties;
import com.deploypilot.provider.github.GitHubGitProvider;
import com.deploypilot.provider.model.*;
import com.deploypilot.provider.netlify.NetlifyHostingProvider;
import com.deploypilot.provider.render.RenderHostingProvider;
import com.deploypilot.repoaccess.RepositoryRef;
import com.deploypilot.verify.LogSanitizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Provider adapters tested directly against the mock server: GitHub never writes
 * the default branch and skips a needless PR; Render never selects a paid plan;
 * provider logs are sanitised.
 */
class ProviderAdapterTest {

    private MockProviderServer mock;
    private GitHubGitProvider github;
    private NetlifyHostingProvider netlify;
    private RenderHostingProvider render;

    private final ProviderCredential gh = new ProviderCredential(ProviderType.GITHUB, ConnectionType.GITHUB_PAT, "ghp_x");
    private final ProviderCredential nf = new ProviderCredential(ProviderType.NETLIFY, ConnectionType.NETLIFY_PAT, "nfp_x");
    private final ProviderCredential rd = new ProviderCredential(ProviderType.RENDER, ConnectionType.RENDER_API_KEY, "rnd_x");

    @BeforeEach
    void setUp() throws IOException {
        mock = new MockProviderServer();
        ProviderApiClient http = new ProviderApiClient();
        ProviderProperties props = new ProviderProperties(mock.githubBaseUrl(), mock.netlifyBaseUrl(), mock.renderBaseUrl(), mock.supabaseBaseUrl());
        LogSanitizer sanitizer = new LogSanitizer();
        github = new GitHubGitProvider(http, props);
        netlify = new NetlifyHostingProvider(http, props, sanitizer);
        render = new RenderHostingProvider(http, props, sanitizer);
    }

    @AfterEach
    void tearDown() { mock.close(); }

    @Test
    void githubOpensBranchAndPrWithoutTouchingMain() {
        RepositoryRef ref = RepositoryRef.parse("demo/sample-monorepo");
        PullRequestResult result = github.openConfigPullRequest(gh, ref, "main",
            "deploypilot/deployment-config", "Add config", "body",
            List.of(new CommitFile("netlify.toml", "[build]\n  command = \"npm run build\"")));

        assertTrue(result.created());
        assertEquals("https://github.com/demo/sample-monorepo/pull/7", result.url());

        // Never wrote to the default branch.
        assertTrue(mock.requests().stream().noneMatch(r ->
                (r.method().equals("PATCH") || r.method().equals("PUT")) && r.path().contains("git/refs/heads/main")),
            "must never update refs/heads/main");
        // Created a dedicated branch ref (not main) and opened exactly one PR.
        MockProviderServer.Recorded refCreate = mock.to("/gh").stream()
            .filter(r -> r.method().equals("POST") && r.path().endsWith("/git/refs")).findFirst().orElseThrow();
        assertTrue(refCreate.body().contains("refs/heads/deploypilot/deployment-config"));
        assertFalse(refCreate.body().contains("refs/heads/main"));
        assertEquals(1, mock.requests().stream()
            .filter(r -> r.method().equals("POST") && r.path().endsWith("/pulls")).count(),
            "exactly one pull request opened");
    }

    @Test
    void githubSkipsPullRequestWhenNoFilesChanged() {
        mock.setGitHubFilesAlreadyPresent(true);
        RepositoryRef ref = RepositoryRef.parse("demo/sample-monorepo");
        PullRequestResult result = github.openConfigPullRequest(gh, ref, "main",
            "deploypilot/deployment-config", "Add config", "body",
            List.of(new CommitFile("netlify.toml", "mock file content that already matches")));

        assertFalse(result.created(), "no PR when the file already matches");
        assertEquals(0, mock.requests().stream()
            .filter(r -> r.method().equals("POST") && r.path().endsWith("/pulls")).count());
        assertEquals(0, mock.requests().stream()
            .filter(r -> r.method().equals("POST") && r.path().endsWith("/git/refs")).count());
    }

    @Test
    void renderCreatesServiceOnFreePlanOnly() {
        HostingSite site = render.createSite(rd, new CreateSiteRequest(
            "myapp-backend", "demo/sample-monorepo", "main", "backend",
            "mvn -q package", null, "java -jar app.jar", "docker", "/api/health"));
        assertNotNull(site.id());

        MockProviderServer.Recorded create = mock.requests().stream()
            .filter(r -> r.method().equals("POST") && r.path().endsWith("/rd/services")).findFirst().orElseThrow();
        assertTrue(create.body().contains("\"plan\":\"free\""), "must request the free plan");
        assertFalse(create.body().toLowerCase().contains("\"plan\":\"starter\""));
        assertFalse(create.body().toLowerCase().contains("\"plan\":\"standard\""));
        assertFalse(create.body().toLowerCase().contains("\"plan\":\"pro\""));
    }

    @Test
    void renderReturnsSanitisedLogs() {
        String logs = render.getSanitizedLogs(rd, "rd-srv-1", "rd-deploy-1");
        assertNotNull(logs);
        assertFalse(logs.contains("rnd_abcdefghij1234567890abcd"), "provider logs must be sanitised");
        assertTrue(logs.contains("[REDACTED]") || logs.contains("live"));
    }

    @Test
    void netlifyDeployReachesLiveAndCapturesUrl() {
        HostingSite site = netlify.createSite(nf, new CreateSiteRequest(
            "myapp-frontend", "demo/sample-monorepo", "main", "frontend",
            "npm run build", "dist", null, null, null, true));
        DeploymentStatus status = netlify.triggerDeploy(nf, site.id(), new DeployRequest("main", null, false));
        DeploymentStatus finalStatus = netlify.getDeploymentStatus(nf, site.id(), status.deploymentId());
        assertEquals(DeploymentState.LIVE, finalStatus.state());
        assertEquals(mock.liveFrontendUrl(), finalStatus.url());
    }

    @Test
    void netlifyReusesLinkedSiteInsteadOfDuplicating() {
        netlify.createSite(nf, new CreateSiteRequest("myapp-frontend", "demo/sample-monorepo", "main",
            "frontend", "npm run build", "dist", null, null, null, true));
        List<HostingSite> sites = netlify.listSites(nf);
        assertEquals(1, sites.size());
        assertEquals("demo/sample-monorepo", sites.get(0).linkedRepo());
    }

    @Test
    void netlifyUsesCurrentRepositoryAndEnvironmentContracts() {
        CreateSiteRequest request = new CreateSiteRequest(
            "myapp-frontend", "demo/sample-monorepo", "main", null,
            "npm run build", "dist", null, null, null, true);
        HostingSite site = netlify.createSite(nf, request);

        MockProviderServer.Recorded create = mock.requests().stream()
            .filter(r -> r.method().equals("POST") && r.path().endsWith("/nf/sites"))
            .findFirst().orElseThrow();
        assertTrue(create.body().contains("\"repo_path\":\"demo/sample-monorepo\""));
        assertTrue(create.body().contains("\"repo_branch\":\"main\""));
        assertTrue(create.body().contains("\"repo_url\":\"https://github.com/demo/sample-monorepo.git\""));
        assertTrue(create.body().contains("\"public_repo\":true"));
        assertFalse(create.body().contains("\"repo\":\"demo/sample-monorepo\""));
        assertFalse(create.body().contains("\"branch\":\"main\""));

        mock.markNetlifyRepoBindingStale(site.id());
        netlify.configureSite(nf, site.id(), request);
        netlify.setEnvVars(nf, site.id(), List.of(new EnvVarInput("VITE_API_URL", "https://api.example", false)));
        netlify.setEnvVars(nf, site.id(), List.of(new EnvVarInput("VITE_API_URL", "https://api-v2.example", false)));

        assertEquals(1, mock.countExact("POST", "/nf/accounts/nf-acct-1/env"),
            "new environment variables use Netlify's account environment endpoint");
        assertEquals(1, mock.countExact("PUT", "/nf/accounts/nf-acct-1/env/VITE_API_URL"),
            "existing environment variables are updated idempotently");
        assertTrue(mock.to("/nf/accounts/nf-acct-1/env").stream()
            .filter(r -> r.method().equals("POST") || r.method().equals("PUT"))
            .noneMatch(r -> r.body().contains("\"scopes\"")),
            "free-plan environment variables must omit paid granular scopes");
        assertEquals(1, mock.countExact("PUT", "/nf/sites/" + site.id() + "/unlink_repo"),
            "a stale deploy key is cleared before relinking the same public site");
        assertTrue(mock.requests().stream()
            .filter(r -> r.method().equals("PUT") && r.path().contains("/unlink_repo"))
            .allMatch(r -> r.body().isBlank()), "Netlify's unlink action has no request body");
        assertTrue(mock.to("/nf/sites/" + site.id()).stream()
            .filter(r -> r.method().equals("PATCH"))
            .allMatch(r -> !r.body().contains("build_settings") && !r.body().contains("\"env\"")),
            "site updates must never use the removed build_settings.env contract");
    }

    @Test
    void netlifyCreatesVariablesWithCurrentPayloadStructure() {
        HostingSite site = createFrontend();
        mock.clearRequests();
        netlify.setEnvVars(nf, site.id(), List.of(
            new EnvVarInput("VITE_API_URL", "https://jobpilot-backend-qg2w.onrender.com", false),
            new EnvVarInput("VITE_SUPABASE_URL", "https://proj.supabase.co", false),
            new EnvVarInput("VITE_SUPABASE_ANON_KEY", "anon-public-key", false)));

        // Reads the site to resolve the account id, then creates via the account env endpoint.
        MockProviderServer.Recorded create = mock.requests().stream()
            .filter(r -> r.method().equals("POST") && r.path().startsWith("/nf/accounts/nf-acct-1/env"))
            .findFirst().orElseThrow();
        assertTrue(create.path().contains("site_id=" + site.id()), "create must pass site_id as a query parameter");
        String b = create.body();
        assertTrue(b.contains("\"key\":\"VITE_API_URL\""));
        assertTrue(b.contains("\"key\":\"VITE_SUPABASE_URL\""));
        assertTrue(b.contains("\"key\":\"VITE_SUPABASE_ANON_KEY\""));
        assertTrue(b.contains("\"context\":\"all\""), "each value applies to all deploy contexts");
        assertTrue(b.contains("\"is_secret\":false"), "public frontend variables are not secret");
        assertFalse(b.contains("\"scopes\""),
            "free-plan variables omit Netlify's paid granular-scope field");
    }

    @Test
    void netlifyUpdatesExistingVariableInPlaceIdempotently() {
        HostingSite site = createFrontend();
        netlify.setEnvVars(nf, site.id(), List.of(new EnvVarInput("VITE_SUPABASE_URL", "https://a.supabase.co", false)));
        mock.clearRequests();
        // Second run with a new value: the variable now exists, so it is updated, not recreated.
        netlify.setEnvVars(nf, site.id(), List.of(new EnvVarInput("VITE_SUPABASE_URL", "https://b.supabase.co", false)));

        assertEquals(0, mock.countExact("POST", "/nf/accounts/nf-acct-1/env"), "no duplicate create");
        assertEquals(1, mock.countExact("PUT", "/nf/accounts/nf-acct-1/env/VITE_SUPABASE_URL"),
            "existing variable is updated in place");
    }

    @Test
    void netlifyRecoversWhenAReusedSiteAlreadyHasTheVariable() {
        HostingSite site = createFrontend();
        // The variable already exists on the reused site but is not returned by the
        // listing (legacy/site-level). Creating it 400s "already exists".
        mock.seedNetlifyHiddenEnvKey("VITE_API_URL");
        mock.clearRequests();

        assertDoesNotThrow(() -> netlify.setEnvVars(nf, site.id(),
            List.of(new EnvVarInput("VITE_API_URL", "https://api.example", false))),
            "a pre-existing variable on a reused site must not fail the deploy");

        assertEquals(1, mock.countExact("POST", "/nf/accounts/nf-acct-1/env"), "one create attempt");
        assertEquals(1, mock.countExact("PUT", "/nf/accounts/nf-acct-1/env/VITE_API_URL"),
            "create-conflict falls back to an idempotent update");
    }

    @Test
    void netlifyFailsWithVariableNameWhenValueIsMissing() {
        HostingSite site = createFrontend();
        mock.clearRequests();

        // Blank value.
        Exception blank = assertThrows(RuntimeException.class, () -> netlify.setEnvVars(nf, site.id(),
            List.of(new EnvVarInput("VITE_SUPABASE_ANON_KEY", "   ", false))));
        assertTrue(blank.getMessage().contains("VITE_SUPABASE_ANON_KEY"), "error names the variable");
        // Null value.
        Exception missing = assertThrows(RuntimeException.class, () -> netlify.setEnvVars(nf, site.id(),
            List.of(new EnvVarInput("VITE_API_URL", null, false))));
        assertTrue(missing.getMessage().contains("VITE_API_URL"), "error names the variable");

        // Validation happens before any Netlify call — nothing was sent.
        assertEquals(0, mock.count("GET", "/nf/accounts/nf-acct-1/env"));
        assertEquals(0, mock.count("POST", "/nf/accounts/nf-acct-1/env"));
    }

    @Test
    void netlifyEnvErrorsAreSanitisedAndNeverLeakSecrets() {
        HostingSite site = createFrontend();
        String anon = "eyJhbGciOiJIUzI1Niop-super-secret-anon";
        // Netlify's failure body echoes a token and the value we sent — neither may surface.
        mock.setNetlifyEnvFailures(1);
        mock.setNetlifyEnvErrorBody("{\"message\":\"invalid token nfp_abcdefghij1234567890zzzz value " + anon + "\"}");
        mock.clearRequests();

        Exception ex = assertThrows(RuntimeException.class, () -> netlify.setEnvVars(nf, site.id(),
            List.of(new EnvVarInput("VITE_SUPABASE_ANON_KEY", anon, false))));

        assertTrue(ex.getMessage().contains("status 400"), "surfaces the status for triage");
        assertFalse(ex.getMessage().contains(anon), "the environment-variable value must never be echoed");
        assertFalse(ex.getMessage().contains("nfp_abcdefghij1234567890zzzz"), "provider tokens must be redacted");
    }

    @Test
    void netlifyForbiddenEnvOperationIsNotMisreportedAsBadToken() {
        HostingSite site = createFrontend();
        mock.setNetlifyEnvForbidden(1);
        mock.clearRequests();

        Exception ex = assertThrows(RuntimeException.class, () -> netlify.setEnvVars(nf, site.id(),
            List.of(new EnvVarInput("VITE_API_URL", "https://api.example", false))));

        assertTrue(ex.getMessage().contains("status 403"));
        assertTrue(ex.getMessage().contains("current plan"));
        assertFalse(ex.getMessage().toLowerCase().contains("rejected the token"));
    }

    // ---------- repository-binding detection (field-presence aware) ----------

    @Test
    void netlifyAcceptsExistingPublicBindingWithoutRepair() {
        HostingSite site = createFrontend(); // build_settings has public_repo=true, deploy_key_id=""
        mock.clearRequests();
        assertDoesNotThrow(() -> netlify.configureSite(nf, site.id(), publicRequest("demo/sample-monorepo", "main")));
        assertEquals(0, unlinkCount(site.id()), "a valid public binding must not be unlinked");
    }

    @Test
    void netlifyAcceptsManualGithubAppLinkWhenOptionalFieldsAbsent() {
        HostingSite site = createFrontend();
        // Simulate the manual relink: repo linked and buildable, but Netlify omits the
        // optional public_repo and deploy_key_id fields (GitHub App connection).
        mock.markNetlifyRepoLinkedViaGithubApp(site.id());
        mock.clearRequests();

        assertDoesNotThrow(() -> netlify.configureSite(nf, site.id(), publicRequest("demo/sample-monorepo", "main")),
            "an absent public_repo/deploy_key_id must not be treated as a broken binding");
        assertEquals(0, unlinkCount(site.id()),
            "a valid manually-linked GitHub App connection must never be unlinked");
    }

    @Test
    void netlifyRepairsExplicitlyStalePublicBinding() {
        HostingSite site = createFrontend();
        mock.markNetlifyRepoBindingStale(site.id()); // public_repo=false + lingering deploy_key_id
        mock.clearRequests();

        assertDoesNotThrow(() -> netlify.configureSite(nf, site.id(), publicRequest("demo/sample-monorepo", "main")));
        assertEquals(1, unlinkCount(site.id()), "a genuinely stale binding is still repaired");
    }

    @Test
    void netlifyRepairsSshUrlThatCausesHostKeyFailure() {
        HostingSite site = createFrontend();
        mock.markNetlifyRepoUsingSsh(site.id());
        mock.clearRequests();

        assertDoesNotThrow(() -> netlify.configureSite(nf, site.id(),
            publicRequest("demo/sample-monorepo", "main")));
        assertEquals(1, unlinkCount(site.id()), "a public SSH repository URL must be cleared");
        assertTrue(mock.to("/nf/sites/" + site.id()).stream()
            .filter(r -> r.method().equals("PATCH"))
            .anyMatch(r -> r.body().contains(
                "\"repo_url\":\"https://github.com/demo/sample-monorepo.git\"")),
            "the repaired public repository must use HTTPS");
    }

    @Test
    void netlifyPreservesPrivateRepositoryBinding() {
        HostingSite site = netlify.createSite(nf, new CreateSiteRequest("priv-frontend", "acme/private-app", "main",
            "frontend", "npm run build", "dist", null, null, null, false)); // private
        mock.markNetlifyRepoLinkedViaGithubApp(site.id());
        mock.clearRequests();

        assertDoesNotThrow(() -> netlify.configureSite(nf, site.id(),
            new CreateSiteRequest(null, "acme/private-app", "main", "frontend",
                "npm run build", "dist", null, null, null, false)));
        assertEquals(0, unlinkCount(site.id()), "a private GitHub App connection must be preserved, not unlinked");
        // The repository payload must never force a private repo through public_repo.
        assertTrue(mock.to("/nf/sites/" + site.id()).stream()
            .filter(r -> r.method().equals("PATCH"))
            .allMatch(r -> !r.body().contains("\"public_repo\"")),
            "private repositories must not be reconfigured as public");
    }

    @Test
    void netlifyAcceptsPartialPatchAfterAVerifyingReRead() {
        HostingSite site = createFrontend();
        mock.markNetlifyRepoBindingStale(site.id()); // force a repair path so a PATCH is issued
        mock.setNetlifyNextPatchOmitsRepoPath(true); // PATCH returns a partial body
        mock.clearRequests();

        HostingSite configured = netlify.configureSite(nf, site.id(), publicRequest("demo/sample-monorepo", "main"));
        assertEquals("demo/sample-monorepo", configured.linkedRepo(),
            "a partial PATCH response is reconciled by re-reading the site");
        // Verified via a follow-up GET rather than trusting the partial PATCH body.
        assertTrue(mock.count("GET", "/nf/sites/" + site.id()) >= 1, "a verifying re-read was performed");
    }

    @Test
    void netlifyConfigureIsIdempotentOnRepeatedRetries() {
        HostingSite site = createFrontend();
        mock.markNetlifyRepoLinkedViaGithubApp(site.id());
        mock.clearRequests();

        netlify.configureSite(nf, site.id(), publicRequest("demo/sample-monorepo", "main"));
        netlify.configureSite(nf, site.id(), publicRequest("demo/sample-monorepo", "main"));

        assertEquals(0, unlinkCount(site.id()), "repeated retries never unlink a valid site");
        assertEquals(0, mock.countExact("POST", "/nf/sites"), "no duplicate site is created");
    }

    private long unlinkCount(String siteId) {
        return mock.countExact("PUT", "/nf/sites/" + siteId + "/unlink_repo");
    }

    private CreateSiteRequest publicRequest(String repo, String branch) {
        return new CreateSiteRequest(null, repo, branch, "frontend", "npm run build", "dist", null, null, null, true);
    }

    private HostingSite createFrontend() {
        return netlify.createSite(nf, new CreateSiteRequest("myapp-frontend", "demo/sample-monorepo", "main",
            "frontend", "npm run build", "dist", null, null, null, true));
    }
}

package com.deploypilot.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end controlled-automation flow against mock providers: connect, plan,
 * confirm, execute, monitor and verify — plus ownership, token secrecy,
 * idempotency, environment-variable destination safety and replay prevention.
 * No real GitHub/Netlify/Render resources are ever touched.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AutomationFlowTest {

    static final MockProviderServer MOCK;
    static {
        try { MOCK = new MockProviderServer(); }
        catch (IOException e) { throw new RuntimeException(e); }
    }

    static final String GH_TOKEN = "ghp_secretmarker_GH_0001";
    static final String NF_TOKEN = "nfp_secretmarker_NF_0002";
    static final String RD_TOKEN = "rnd_secretmarker_RD_0003";
    static final String DB_URL_VALUE = "postgresql://dbuser:dbSECRET987@db.example.com:5432/app";

    @DynamicPropertySource
    static void providerUrls(DynamicPropertyRegistry registry) {
        registry.add("deploypilot.providers.github-api-base-url", MOCK::githubBaseUrl);
        registry.add("deploypilot.providers.netlify-api-base-url", MOCK::netlifyBaseUrl);
        registry.add("deploypilot.providers.render-api-base-url", MOCK::renderBaseUrl);
    }

    @AfterAll
    static void stop() { MOCK.close(); }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void resetMock() { MOCK.reset(); }

    // ==================== the happy path ====================

    @Test
    void fullDeploymentDeploysBothTiersAndRunsVerification() throws Exception {
        String token = register();
        connectAll(token);
        long projectId = importRepo(token);
        saveSecret(token, projectId, "DATABASE_URL", DB_URL_VALUE);

        JsonNode plan = plan(token, projectId);
        assertTrue(plan.path("executable").asBoolean(), "plan should be executable once connected and DB supplied");
        assertEquals("DeployPilot will only perform the actions shown below after you confirm.",
            plan.path("consentNotice").asText());
        String planHash = plan.path("planHash").asText();

        JsonNode confirmation = confirm(token, projectId, planHash);
        long runId = confirmation.path("runId").asLong();
        String nonce = confirmation.path("nonce").asText();

        execute(token, projectId, runId, nonce);
        JsonNode run = pollRun(token, projectId, runId);

        assertEquals("SUCCEEDED", run.path("status").asText(), () -> "run failed: "
            + run.path("failureReason").asText() + " steps=" + run.path("steps"));

        // Captured production URLs.
        assertEquals(MOCK.liveBackendUrl(), run.path("outputs").path("backendUrl").asText());
        assertEquals(MOCK.liveFrontendUrl(), run.path("outputs").path("frontendUrl").asText());

        // Stage 3 verification ran automatically after deployment.
        assertTrue(run.path("verificationRunId").isNumber(), "verification should have run");
        assertNotNull(run.path("verificationStatus").asText(null));
        boolean verifyStepRan = false;
        for (JsonNode s : run.path("steps")) {
            if ("verify".equals(s.path("id").asText())) verifyStepRan = "SUCCEEDED".equals(s.path("status").asText());
        }
        assertTrue(verifyStepRan, "verify step should have executed");

        // Exactly one service and one site created (no duplicates in a single run).
        assertEquals(1, MOCK.countExact("POST", "/rd/services"), "one backend service created");
        assertEquals(1, MOCK.countExact("POST", "/nf/sites"), "one frontend site created");

        // Backend secrets went to Render.
        assertTrue(MOCK.requests().stream().anyMatch(r ->
            r.method().equals("PUT") && r.path().endsWith("/env-vars/JWT_SECRET")));
        assertTrue(MOCK.requests().stream().anyMatch(r ->
            r.method().equals("PUT") && r.path().endsWith("/env-vars/DATABASE_URL")));
    }

    @Test
    void frontendNeverReceivesBackendSecrets() throws Exception {
        String token = register();
        connectAll(token);
        long projectId = importRepo(token);
        saveSecret(token, projectId, "DATABASE_URL", DB_URL_VALUE);

        runToCompletion(token, projectId);

        // The Netlify environment endpoint must carry the public API URL but no backend secrets.
        String netlifyEnvBodies = MOCK.requests().stream()
            .filter(r -> r.path().contains("/nf/accounts/") && r.path().contains("/env"))
            .map(MockProviderServer.Recorded::body)
            .reduce("", (a, b) -> a + "\n" + b);

        assertTrue(netlifyEnvBodies.contains("VITE_API_BASE_URL"), "frontend gets its API URL");
        assertTrue(netlifyEnvBodies.contains(MOCK.liveBackendUrl()), "API URL is the captured backend URL");
        assertFalse(netlifyEnvBodies.contains("JWT_SECRET"), "no JWT secret sent to Netlify");
        assertFalse(netlifyEnvBodies.contains("DATABASE_URL"), "no database variable sent to Netlify");
        assertFalse(netlifyEnvBodies.contains("dbSECRET987"), "no secret value sent to Netlify");
    }

    @Test
    void dependencyOrderSetsFrontendApiUrlFromBackend() throws Exception {
        String token = register();
        connectAll(token);
        long projectId = importRepo(token);
        saveSecret(token, projectId, "DATABASE_URL", DB_URL_VALUE);
        runToCompletion(token, projectId);

        // The frontend API URL must be the backend origin exactly. The application
        // owns its route prefix; adding /api here would create /api/api requests for
        // clients such as JobPilot that already append /api in their request paths.
        String netlifyEnvBodies = MOCK.requests().stream()
            .filter(r -> r.path().contains("/nf/accounts/") && r.path().contains("/env"))
            .map(MockProviderServer.Recorded::body).reduce("", (a, b) -> a + b);
        assertTrue(netlifyEnvBodies.contains(MOCK.liveBackendUrl()),
            "frontend API URL is derived from the captured backend URL");
        assertFalse(netlifyEnvBodies.contains(MOCK.liveBackendUrl() + "/api"),
            "DeployPilot must not guess an API route prefix");
    }

    // ==================== idempotency / duplicate prevention ====================

    @Test
    void secondRunReusesResourcesWithoutDuplicates() throws Exception {
        String token = register();
        connectAll(token);
        long projectId = importRepo(token);
        saveSecret(token, projectId, "DATABASE_URL", DB_URL_VALUE);

        runToCompletion(token, projectId);
        MOCK.clearRequests(); // keep provider state, forget request history

        runToCompletion(token, projectId);
        assertEquals(0, MOCK.countExact("POST", "/rd/services"), "backend service must be reused, not recreated");
        assertEquals(0, MOCK.countExact("POST", "/nf/sites"), "frontend site must be reused, not recreated");
    }

    @Test
    void retryRepairsExistingNetlifySiteBeforeResumingFailedEnvironmentStep() throws Exception {
        String token = register();
        connectAll(token);
        long projectId = importRepo(token);
        saveSecret(token, projectId, "DATABASE_URL", DB_URL_VALUE);

        JsonNode plan = plan(token, projectId);
        JsonNode confirmation = confirm(token, projectId, plan.path("planHash").asText());
        long runId = confirmation.path("runId").asLong();
        MOCK.setNetlifyEnvFailures(1);
        execute(token, projectId, runId, confirmation.path("nonce").asText());
        JsonNode failed = pollRun(token, projectId, runId);
        assertEquals("FAILED", failed.path("status").asText());
        assertEquals(1, MOCK.countExact("POST", "/nf/sites"), "the site was created before the env failure");

        MOCK.clearRequests(); // preserve the existing site, as a real failed run does
        JsonNode retryConfirmation = confirm(token, projectId, plan.path("planHash").asText());
        retry(token, projectId, runId, retryConfirmation.path("nonce").asText());
        JsonNode retried = pollRun(token, projectId, runId);

        assertEquals("SUCCEEDED", retried.path("status").asText(), () -> "retry failed: "
            + retried.path("failureReason").asText() + " steps=" + retried.path("steps"));
        assertEquals(0, MOCK.countExact("POST", "/nf/sites"), "retry must not create a duplicate site");
        assertTrue(MOCK.requests().stream().anyMatch(r ->
            r.method().equals("PATCH") && r.path().startsWith("/nf/sites/")
                && r.body().contains("\"repo_path\":\"demo/sample-monorepo\"")
                && r.body().contains("\"public_repo\":true")),
            "retry must repair the existing site's public GitHub repository settings");
        assertTrue(MOCK.countExact("POST", "/nf/accounts/nf-acct-1/env") > 0,
            "retry must resume using the current Netlify environment endpoint");
    }

    // ==================== confirmation & safety ====================

    @Test
    void confirmRejectsAStaleOrWrongPlanHash() throws Exception {
        String token = register();
        connectAll(token);
        long projectId = importRepo(token);
        saveSecret(token, projectId, "DATABASE_URL", DB_URL_VALUE);
        plan(token, projectId);

        mockMvc.perform(post("/projects/" + projectId + "/automation/confirm")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"DEPLOY_FOR_ME\",\"planHash\":\"deadbeefdeadbeef\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void confirmationCannotBeReplayed() throws Exception {
        String token = register();
        connectAll(token);
        long projectId = importRepo(token);
        saveSecret(token, projectId, "DATABASE_URL", DB_URL_VALUE);

        String planHash = plan(token, projectId).path("planHash").asText();
        JsonNode confirmation = confirm(token, projectId, planHash);
        long runId = confirmation.path("runId").asLong();
        String nonce = confirmation.path("nonce").asText();

        execute(token, projectId, runId, nonce);
        pollRun(token, projectId, runId);

        // Re-presenting the same confirmation must be refused.
        mockMvc.perform(post("/projects/" + projectId + "/automation/runs/" + runId + "/execute")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nonce\":\"" + nonce + "\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void guideMeModeIsNeverExecutable() throws Exception {
        String token = register();
        connectAll(token);
        long projectId = importRepo(token);
        saveSecret(token, projectId, "DATABASE_URL", DB_URL_VALUE);

        MvcResult result = mockMvc.perform(post("/projects/" + projectId + "/automation/plan")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"GUIDE_ME\"}"))
            .andExpect(status().isOk()).andReturn();
        JsonNode plan = data(result);
        assertFalse(plan.path("executable").asBoolean(), "Guide Me must never be executable");

        // Confirming a non-Deploy-for-me plan is rejected.
        mockMvc.perform(post("/projects/" + projectId + "/automation/confirm")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"GUIDE_ME\",\"planHash\":\"" + plan.path("planHash").asText() + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void planIsBlockedUntilDatabaseConnectionSupplied() throws Exception {
        String token = register();
        connectAll(token);
        long projectId = importRepo(token);
        // No DATABASE_URL secret supplied yet.
        JsonNode plan = plan(token, projectId);
        assertFalse(plan.path("executable").asBoolean());
        assertTrue(plan.path("blockers").toString().toLowerCase().contains("database"));
    }

    // ==================== ownership & secrecy ====================

    @Test
    void providerTokensAreNeverReturned() throws Exception {
        String token = register();
        connectAll(token);

        String body = mockMvc.perform(get("/connections").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains(GH_TOKEN));
        assertFalse(body.contains(NF_TOKEN));
        assertFalse(body.contains(RD_TOKEN));
        // Status and account label are shown instead.
        assertTrue(body.contains("CONNECTED"));
        assertTrue(body.contains("octo-user"));
    }

    @Test
    void storedSecretValuesAreNeverReturned() throws Exception {
        String token = register();
        long projectId = importRepo(token);
        saveSecret(token, projectId, "DATABASE_URL", DB_URL_VALUE);

        String body = mockMvc.perform(get("/projects/" + projectId + "/automation/secrets")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("dbSECRET987"), "secret values must never be returned");
        assertTrue(body.contains("DATABASE_URL"));
    }

    @Test
    void crossUserCannotAccessAnotherProjectsAutomation() throws Exception {
        String owner = register();
        connectAll(owner);
        long projectId = importRepo(owner);
        saveSecret(owner, projectId, "DATABASE_URL", DB_URL_VALUE);
        String planHash = plan(owner, projectId).path("planHash").asText();
        long runId = confirm(owner, projectId, planHash).path("runId").asLong();

        String attacker = register();
        mockMvc.perform(get("/projects/" + projectId + "/automation/runs/" + runId)
                .header("Authorization", "Bearer " + attacker))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/projects/" + projectId + "/automation/plan")
                .header("Authorization", "Bearer " + attacker)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/projects/" + projectId + "/automation/secrets")
                .header("Authorization", "Bearer " + attacker))
            .andExpect(status().isForbidden());
    }

    // ==================== helpers ====================

    private String register() throws Exception {
        String username = "auto" + System.nanoTime();
        MvcResult result = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "username", username, "email", username + "@example.com", "password", "password123"))))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private void connectAll(String token) throws Exception {
        connect(token, "github", GH_TOKEN);
        connect(token, "netlify", NF_TOKEN);
        connect(token, "render", RD_TOKEN);
    }

    private void connect(String token, String provider, String providerToken) throws Exception {
        mockMvc.perform(post("/connections/" + provider)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + providerToken + "\"}"))
            .andExpect(status().isOk());
    }

    private long importRepo(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/projects/import")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repository\":\"demo/sample-monorepo\"}"))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
            .path("data").path("projectId").asLong();
    }

    private void saveSecret(String token, long projectId, String name, String value) throws Exception {
        mockMvc.perform(put("/projects/" + projectId + "/automation/secrets")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", name, "value", value, "destination", "Backend service"))))
            .andExpect(status().isOk());
    }

    private JsonNode plan(String token, long projectId) throws Exception {
        MvcResult result = mockMvc.perform(post("/projects/" + projectId + "/automation/plan")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"DEPLOY_FOR_ME\"}"))
            .andExpect(status().isOk()).andReturn();
        return data(result);
    }

    private JsonNode confirm(String token, long projectId, String planHash) throws Exception {
        MvcResult result = mockMvc.perform(post("/projects/" + projectId + "/automation/confirm")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"DEPLOY_FOR_ME\",\"planHash\":\"" + planHash + "\"}"))
            .andExpect(status().isOk()).andReturn();
        return data(result);
    }

    private void execute(String token, long projectId, long runId, String nonce) throws Exception {
        mockMvc.perform(post("/projects/" + projectId + "/automation/runs/" + runId + "/execute")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nonce\":\"" + nonce + "\"}"))
            .andExpect(status().isOk());
    }

    private void retry(String token, long projectId, long runId, String nonce) throws Exception {
        mockMvc.perform(post("/projects/" + projectId + "/automation/runs/" + runId + "/retry")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nonce\":\"" + nonce + "\"}"))
            .andExpect(status().isOk());
    }

    private JsonNode runToCompletion(String token, long projectId) throws Exception {
        String planHash = plan(token, projectId).path("planHash").asText();
        JsonNode confirmation = confirm(token, projectId, planHash);
        long runId = confirmation.path("runId").asLong();
        execute(token, projectId, runId, confirmation.path("nonce").asText());
        JsonNode run = pollRun(token, projectId, runId);
        assertEquals("SUCCEEDED", run.path("status").asText(), () -> "run failed: "
            + run.path("failureReason").asText() + " steps=" + run.path("steps"));
        return run;
    }

    private JsonNode pollRun(String token, long projectId, long runId) throws Exception {
        for (int i = 0; i < 150; i++) {
            MvcResult result = mockMvc.perform(get("/projects/" + projectId + "/automation/runs/" + runId)
                    .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
            JsonNode run = data(result);
            String status = run.path("status").asText();
            if (!status.equals("RUNNING") && !status.equals("PENDING")) return run;
            Thread.sleep(200);
        }
        throw new AssertionError("run did not complete in time");
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}

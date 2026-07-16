package com.deploypilot.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
 * Controlled Supabase automation end-to-end against the mock Management API:
 * create-on-free-plan, idempotency, credential routing (service-role/password
 * stay backend-only and never enter run JSON), plan-hash sensitivity to database
 * inputs, billing-pause without pretending success, and Stage 3 verification
 * after deployment (tests 12, 15, 16, 17, 18, 20).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SupabaseAutomationTest {

    static final MockProviderServer MOCK;
    static {
        try { MOCK = new MockProviderServer(); }
        catch (IOException e) { throw new RuntimeException(e); }
    }

    @DynamicPropertySource
    static void providerUrls(DynamicPropertyRegistry registry) {
        registry.add("deploypilot.providers.github-api-base-url", MOCK::githubBaseUrl);
        registry.add("deploypilot.providers.netlify-api-base-url", MOCK::netlifyBaseUrl);
        registry.add("deploypilot.providers.render-api-base-url", MOCK::renderBaseUrl);
        registry.add("deploypilot.providers.supabase-api-base-url", MOCK::supabaseBaseUrl);
    }

    @AfterAll
    static void stop() { MOCK.close(); }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void resetMock() { MOCK.reset(); }

    private static final String CREATE_INPUTS =
        "{\"mode\":\"DEPLOY_FOR_ME\",\"databaseChoice\":\"CREATE_SUPABASE_PROJECT\","
        + "\"supabaseOrgId\":\"org-1\",\"supabaseProjectName\":\"my-db\",\"supabaseRegion\":\"us-east-1\",\"applyMigrations\":false}";

    @Test
    void createsSupabaseProjectOnFreePlanAndKeepsSecretsOutOfRunJson() throws Exception {
        String token = register();
        connectAll(token);
        long projectId = importRepo(token);

        JsonNode plan = plan(token, projectId, CREATE_INPUTS);
        assertTrue(plan.path("executable").asBoolean(), () -> "plan not executable: " + plan.path("blockers"));
        assertTrue(hasAction(plan, "database.create"), "should plan a Supabase project creation");
        assertTrue(hasAction(plan, "database.credentials"), "should prepare DB credentials");
        assertTrue(hasAction(plan, "backend.database-env"), "should set backend DB variables");

        JsonNode run = runToCompletion(token, projectId, CREATE_INPUTS);

        // Supabase outputs captured; free plan requested.
        assertFalse(run.path("outputs").path("supabaseProjectRef").asText().isBlank());
        assertFalse(run.path("outputs").path("supabaseProjectUrl").asText().isBlank());
        assertEquals(1, MOCK.countExact("POST", "/sb/projects"), "exactly one project created (idempotent)");
        assertTrue(MOCK.to("/sb/projects").get(0).body().contains("\"plan\":\"free\""), "free plan only");

        // Test 16: no database password or service-role key anywhere in the run JSON.
        String runJson = run.toString();
        assertFalse(runJson.contains(MockProviderServer.SUPABASE_SERVICE_ROLE_MARKER), "service-role key leaked into run JSON");
        assertFalse(runJson.contains("postgresql://postgres:"), "database URL with password leaked into run JSON");

        // Test 15: the service-role key never went to Netlify (frontend host).
        assertTrue(MOCK.to("/nf").stream().noneMatch(r -> r.body().contains(MockProviderServer.SUPABASE_SERVICE_ROLE_MARKER)),
            "service-role key must never be sent to Netlify");
        assertEquals(0, MOCK.requests().stream()
            .filter(r -> r.path().startsWith("/nf") && r.path().contains("SUPABASE_SERVICE_ROLE_KEY")).count());
        // But the backend DB variable did go to Render.
        assertTrue(MOCK.requests().stream().anyMatch(r ->
            r.method().equals("PUT") && r.path().startsWith("/rd") && r.path().endsWith("/env-vars/DATABASE_URL")));

        // Test 20: Stage 3 verification ran after deployment.
        assertTrue(run.path("verificationRunId").isNumber(), "verification should have run");
    }

    @Test
    void databaseInputsChangeThePlanHash() throws Exception {
        String token = register();
        connectAll(token);
        MOCK.seedSupabaseProject("existing-1", "existing");
        long projectId = importRepo(token);

        String createHash = plan(token, projectId, CREATE_INPUTS).path("planHash").asText();
        String existingInputs = "{\"mode\":\"DEPLOY_FOR_ME\",\"databaseChoice\":\"EXISTING_SUPABASE_PROJECT\",\"supabaseProjectRef\":\"existing-1\"}";
        String existingHash = plan(token, projectId, existingInputs).path("planHash").asText();
        String manualHash = plan(token, projectId, "{\"mode\":\"DEPLOY_FOR_ME\"}").path("planHash").asText();

        assertNotEquals(createHash, existingHash, "different database choices must produce different plan hashes");
        assertNotEquals(createHash, manualHash, "database choice must change the plan hash (drift protection)");
    }

    @Test
    void billingRequiredPausesWithoutPretendingSuccess() throws Exception {
        String token = register();
        connectAll(token);
        long projectId = importRepo(token);
        MOCK.setSupabaseBillingRequired(true);

        String planHash = plan(token, projectId, CREATE_INPUTS).path("planHash").asText();
        JsonNode confirmation = confirm(token, projectId, planHash, CREATE_INPUTS);
        long runId = confirmation.path("runId").asLong();
        execute(token, projectId, runId, confirmation.path("nonce").asText());
        JsonNode run = pollRun(token, projectId, runId);

        assertEquals("PAUSED", run.path("status").asText(), "must pause, not fail or pretend success");
        assertTrue(run.path("failureReason").asText().toLowerCase().contains("free")
            || run.path("failureReason").asText().toLowerCase().contains("paid"), "explains the billing limit");
        assertNotEquals("SUCCEEDED", run.path("status").asText());
    }

    // ==================== helpers ====================

    private String register() throws Exception {
        String username = "sb" + System.nanoTime();
        MvcResult result = mockMvc.perform(post("/auth/register")
                .contentType(MediaType_JSON())
                .content(objectMapper.writeValueAsString(Map.of(
                    "username", username, "email", username + "@example.com", "password", "password123"))))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private void connectAll(String token) throws Exception {
        connect(token, "github", "ghp_secret_gh");
        connect(token, "netlify", "nfp_secret_nf");
        connect(token, "render", "rnd_secret_rd");
        connect(token, "supabase", "sbp_secret_sb");
    }

    private void connect(String token, String provider, String providerToken) throws Exception {
        mockMvc.perform(post("/connections/" + provider)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType_JSON())
                .content("{\"token\":\"" + providerToken + "\"}"))
            .andExpect(status().isOk());
    }

    private long importRepo(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/projects/import")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType_JSON())
                .content("{\"repository\":\"demo/sample-monorepo\"}"))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("projectId").asLong();
    }

    private JsonNode plan(String token, long projectId, String inputs) throws Exception {
        MvcResult result = mockMvc.perform(post("/projects/" + projectId + "/automation/plan")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType_JSON()).content(inputs))
            .andExpect(status().isOk()).andReturn();
        return data(result);
    }

    private JsonNode confirm(String token, long projectId, String planHash, String inputs) throws Exception {
        String body = inputs.substring(0, inputs.length() - 1) + ",\"planHash\":\"" + planHash + "\"}";
        MvcResult result = mockMvc.perform(post("/projects/" + projectId + "/automation/confirm")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType_JSON()).content(body))
            .andExpect(status().isOk()).andReturn();
        return data(result);
    }

    private void execute(String token, long projectId, long runId, String nonce) throws Exception {
        mockMvc.perform(post("/projects/" + projectId + "/automation/runs/" + runId + "/execute")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType_JSON()).content("{\"nonce\":\"" + nonce + "\"}"))
            .andExpect(status().isOk());
    }

    private JsonNode runToCompletion(String token, long projectId, String inputs) throws Exception {
        String planHash = plan(token, projectId, inputs).path("planHash").asText();
        JsonNode confirmation = confirm(token, projectId, planHash, inputs);
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

    private boolean hasAction(JsonNode plan, String id) {
        for (JsonNode a : plan.path("actions")) if (id.equals(a.path("id").asText())) return true;
        return false;
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private static org.springframework.http.MediaType MediaType_JSON() {
        return org.springframework.http.MediaType.APPLICATION_JSON;
    }
}

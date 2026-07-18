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
 * Regression tests for the CREATE_NEW Supabase controlled-deployment plan using a
 * JobPilot-shaped fixture (React/Vite + Express + Supabase, with supabase/schema.sql).
 * Covers: ordered create→wait→credentials→schema before hosting; no service-role-key
 * blocker; anon key to Netlify and service-role key to Render; schema detection with
 * checksum + SQL-safety; destructive-SQL blocking; env-var hygiene (PORT/optional);
 * and exact-commit resolution.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobPilotCreateNewFlowTest {

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

    @AfterAll static void stop() { MOCK.close(); }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach void resetMock() { MOCK.reset(); }

    private static final String CREATE_INPUTS =
        "{\"mode\":\"DEPLOY_FOR_ME\",\"databaseChoice\":\"CREATE_SUPABASE_PROJECT\","
        + "\"supabaseOrgId\":\"org-1\",\"supabaseProjectName\":\"jobpilot-db\",\"supabaseRegion\":\"us-east-1\","
        + "\"applyMigrations\":true}";

    // The value the UI/clients actually send for "Create new".
    private static final String CREATE_NEW_INPUTS =
        "{\"mode\":\"DEPLOY_FOR_ME\",\"databaseChoice\":\"CREATE_NEW\","
        + "\"supabaseOrgId\":\"org-1\",\"supabaseProjectName\":\"jobpilot-db\",\"supabaseRegion\":\"us-east-1\","
        + "\"applyMigrations\":true}";

    // ==================== CREATE_NEW literal is recognised ====================

    @Test
    void createNewLiteralProducesSupabaseActionsAndNoUserSecretPrompt() throws Exception {
        String token = register();
        connectAll(token);
        long projectId = importRepo(token, "demo/jobpilot");

        JsonNode plan = plan(token, projectId, CREATE_NEW_INPUTS);

        // 1 & 2: the Supabase creation action exists and the plan is not blocked.
        assertEquals("CREATE_SUPABASE_PROJECT", plan.path("database").path("choice").asText(),
            "CREATE_NEW must map to the create-project choice");
        assertTrue(has(plan, "database.create"), "CREATE_NEW must produce a Supabase creation action");
        assertTrue(has(plan, "database.wait"));
        assertTrue(has(plan, "database.credentials"));
        assertTrue(plan.path("executable").asBoolean(), () -> "not executable, blockers=" + plan.path("blockers"));

        // 3: the service-role key and anon key are NOT requested from the user.
        for (JsonNode b : plan.path("blockers")) {
            String s = b.asText().toUpperCase();
            assertFalse(s.contains("SERVICE_ROLE"), "must not ask for the service-role key");
            assertFalse(s.contains("ANON_KEY"), "must not ask for the anon key");
        }
        // 5: they are outputs of the creation step, marked FROM_PREVIOUS_STEP.
        assertEquals("FROM_PREVIOUS_STEP", envItem(plan, "SUPABASE_SERVICE_ROLE_KEY").path("valueStatus").asText());
        assertEquals("FROM_PREVIOUS_STEP", envItem(plan, "VITE_SUPABASE_ANON_KEY").path("valueStatus").asText());
    }

    // ==================== plan-structure regression ====================

    @Test
    void createNewPlanIsOrderedAndRoutesVariablesCorrectly() throws Exception {
        String token = register();
        connectAll(token);
        long projectId = importRepo(token, "demo/jobpilot");

        JsonNode plan = plan(token, projectId, CREATE_INPUTS);
        assertTrue(plan.path("executable").asBoolean(), () -> "not executable, blockers=" + plan.path("blockers"));

        // 1 & 7: Supabase create → wait → credentials → schema, all before hosting.
        assertTrue(has(plan, "database.create"), "missing Supabase creation action");
        assertTrue(has(plan, "database.wait"));
        assertTrue(has(plan, "database.credentials"));
        assertTrue(has(plan, "database.migrations.inspect"), "schema should be part of the plan");
        int create = order(plan, "database.create");
        int wait = order(plan, "database.wait");
        int creds = order(plan, "database.credentials");
        int schema = order(plan, "database.migrations.inspect");
        int backendEnsure = order(plan, "backend.ensure");
        int frontendEnsure = order(plan, "frontend.ensure");
        int cors = order(plan, "backend.cors");
        int restart = order(plan, "backend.restart");
        int verify = order(plan, "verify");
        assertTrue(create < wait && wait < creds, "create → wait → credentials");
        assertTrue(creds < schema, "credentials must come before schema");
        assertTrue(schema < backendEnsure, "schema before backend hosting");
        assertTrue(backendEnsure < frontendEnsure, "Render before Netlify");
        assertTrue(frontendEnsure < cors && cors < restart && restart < verify, "CORS → restart → verification");

        // 2: never asks the user for the service-role key.
        for (JsonNode b : plan.path("blockers")) {
            assertFalse(b.asText().toUpperCase().contains("SERVICE_ROLE"),
                "must not block asking for the service-role key: " + b.asText());
        }

        // 3: variable routing.
        assertTrue(destination(plan, "VITE_SUPABASE_ANON_KEY").contains("Frontend"), "anon key → frontend");
        assertTrue(destination(plan, "VITE_SUPABASE_URL").contains("Frontend"), "public URL → frontend");
        assertTrue(destination(plan, "SUPABASE_SERVICE_ROLE_KEY").contains("Backend"), "service-role → backend");
        assertTrue(destination(plan, "SUPABASE_URL").contains("Backend"), "SUPABASE_URL → backend");

        // 4: schema.sql detected with checksum + SQL-safety result.
        JsonNode schemaMig = migration(plan, "schema.sql");
        assertNotNull(schemaMig, "supabase/schema.sql should be detected as database setup");
        assertFalse(schemaMig.path("checksum").asText().isBlank(), "schema checksum shown");
        assertEquals("SAFE", schemaMig.path("safetyClassification").asText(), "safe schema classified SAFE");

        // 5: env hygiene — PORT never configured; unset optional GEMINI_MODEL excluded; AI_PROVIDER supplied.
        assertFalse(envInAnyAction(plan, "PORT"), "PORT must never be configured by DeployPilot");
        assertFalse(envInAnyAction(plan, "GEMINI_MODEL"), "unset optional GEMINI_MODEL must not be pushed");
        assertTrue(envInAnyAction(plan, "AI_PROVIDER"), "AI_PROVIDER should be set");
        assertEquals("MANAGED_BY_PLATFORM", envItem(plan, "PORT").path("valueStatus").asText());

        // 6: exact commit SHA resolved and present in the plan.
        assertFalse(plan.path("commitSha").asText().isBlank(), "latest commit SHA should be resolved");
    }

    // ==================== destructive SQL is blocked ====================

    @Test
    void destructiveSchemaIsBlockedAndNotApplied() throws Exception {
        String token = register();
        connectAll(token);
        long projectId = importRepo(token, "demo/jobpilot-destructive");

        JsonNode plan = plan(token, projectId, CREATE_INPUTS);
        assertNotNull(migration(plan, "schema.sql"), "destructive schema should still be detected");
        assertEquals("POTENTIALLY_DESTRUCTIVE", migration(plan, "schema.sql").path("safetyClassification").asText());
        assertFalse(has(plan, "database.migrations.apply"), "destructive SQL must not get an apply action");
        boolean blocked = false;
        for (JsonNode b : plan.path("blockers")) {
            if (b.asText().toLowerCase().contains("destructive")) blocked = true;
        }
        assertTrue(blocked, "a destructive-SQL blocker is required");
        assertFalse(plan.path("executable").asBoolean(), "a destructive schema must block execution");
    }

    // ==================== end-to-end CREATE_NEW flow ====================

    @Test
    void createNewFlowRunsEndToEndWithCorrectSecretRouting() throws Exception {
        String token = register();
        connectAll(token);
        long projectId = importRepo(token, "demo/jobpilot");
        saveSecret(token, projectId, "GEMINI_API_KEY", "gm-secret-key", "Backend service");

        JsonNode run = runToCompletion(token, projectId, CREATE_INPUTS);

        // The new project was created on the free plan and the schema was applied.
        assertEquals(1, MOCK.countExact("POST", "/sb/projects"), "one project created");
        assertTrue(MOCK.requests().stream().anyMatch(r ->
            r.method().equals("POST") && r.path().equals("/sb/projects") && r.body().contains("\"plan\":\"free\"")),
            "free plan only");
        assertTrue(MOCK.requests().stream().anyMatch(r ->
            r.method().equals("POST") && r.path().contains("/sb/projects/") && r.path().endsWith("/database/query")),
            "repository schema should be applied to Supabase");

        // Anon/public values went to Netlify (env in the request body); service-role/DB
        // secrets went to Render (per-variable path).
        assertTrue(netlifyGot("VITE_SUPABASE_ANON_KEY"), "anon key → Netlify");
        assertTrue(netlifyGot("VITE_SUPABASE_URL"), "public URL → Netlify");
        assertTrue(renderGot("SUPABASE_SERVICE_ROLE_KEY"), "service-role → Render");
        assertTrue(renderGot("SUPABASE_URL"), "SUPABASE_URL → Render");
        assertTrue(renderGot("AI_PROVIDER"), "AI_PROVIDER → Render");
        assertTrue(renderGot("GEMINI_API_KEY"), "saved GEMINI_API_KEY → Render");

        // Safety: service-role key never reaches Netlify or the run JSON; PORT/GEMINI_MODEL never pushed.
        assertFalse(netlifyGot("SUPABASE_SERVICE_ROLE_KEY"), "service-role must never reach Netlify");
        assertFalse(run.toString().contains(MockProviderServer.SUPABASE_SERVICE_ROLE_MARKER), "service-role leaked into run JSON");
        assertFalse(renderGot("PORT") || netlifyGot("PORT"), "PORT must never be sent to a provider");
        assertFalse(renderGot("GEMINI_MODEL") || netlifyGot("GEMINI_MODEL"), "unset GEMINI_MODEL must never be pushed");

        assertTrue(run.path("verificationRunId").isNumber(), "verification runs after deployment");
    }

    // ==================== helpers ====================

    /** Render sets each variable via its own path: PUT /rd/.../env-vars/{name}. */
    private boolean renderGot(String varName) {
        return MOCK.requests().stream().anyMatch(r ->
            r.method().equals("PUT") && r.path().startsWith("/rd") && r.path().endsWith("/env-vars/" + varName));
    }

    /** Netlify sets variables in the request body (build_settings.env), not the path. */
    private boolean netlifyGot(String varName) {
        return MOCK.requests().stream().anyMatch(r ->
            r.path().startsWith("/nf") && r.body() != null && r.body().contains("\"" + varName + "\""));
    }

    private boolean has(JsonNode plan, String id) {
        for (JsonNode a : plan.path("actions")) if (id.equals(a.path("id").asText())) return true;
        return false;
    }

    private int order(JsonNode plan, String id) {
        for (JsonNode a : plan.path("actions")) if (id.equals(a.path("id").asText())) return a.path("order").asInt();
        fail("missing action " + id);
        return -1;
    }

    private JsonNode envItem(JsonNode plan, String name) {
        for (JsonNode e : plan.path("environmentVariables")) if (name.equals(e.path("name").asText())) return e;
        fail("missing env var " + name);
        return null;
    }

    private String destination(JsonNode plan, String name) {
        return envItem(plan, name).path("destination").asText();
    }

    private boolean envInAnyAction(JsonNode plan, String name) {
        for (JsonNode a : plan.path("actions")) {
            for (JsonNode n : a.path("environmentVariableNames")) if (name.equals(n.asText())) return true;
        }
        return false;
    }

    private JsonNode migration(JsonNode plan, String name) {
        for (JsonNode mv : plan.path("database").path("migrations")) {
            if (name.equals(mv.path("name").asText())) return mv;
        }
        return null;
    }

    private String register() throws Exception {
        String username = "jp" + System.nanoTime();
        MvcResult result = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
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
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + providerToken + "\"}"))
            .andExpect(status().isOk());
    }

    private long importRepo(String token, String repo) throws Exception {
        MvcResult result = mockMvc.perform(post("/projects/import")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repository\":\"" + repo + "\"}"))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("projectId").asLong();
    }

    private void saveSecret(String token, long projectId, String name, String value, String destination) throws Exception {
        mockMvc.perform(put("/projects/" + projectId + "/automation/secrets")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", name, "value", value, "destination", destination))))
            .andExpect(status().isOk());
    }

    private JsonNode plan(String token, long projectId, String inputs) throws Exception {
        MvcResult result = mockMvc.perform(post("/projects/" + projectId + "/automation/plan")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(inputs))
            .andExpect(status().isOk()).andReturn();
        return data(result);
    }

    private JsonNode runToCompletion(String token, long projectId, String inputs) throws Exception {
        String planHash = plan(token, projectId, inputs).path("planHash").asText();
        String body = inputs.substring(0, inputs.length() - 1) + ",\"planHash\":\"" + planHash + "\"}";
        MvcResult confirmResult = mockMvc.perform(post("/projects/" + projectId + "/automation/confirm")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn();
        JsonNode confirmation = data(confirmResult);
        long runId = confirmation.path("runId").asLong();
        mockMvc.perform(post("/projects/" + projectId + "/automation/runs/" + runId + "/execute")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nonce\":\"" + confirmation.path("nonce").asText() + "\"}"))
            .andExpect(status().isOk());
        for (int i = 0; i < 150; i++) {
            MvcResult result = mockMvc.perform(get("/projects/" + projectId + "/automation/runs/" + runId)
                    .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
            JsonNode run = data(result);
            String status = run.path("status").asText();
            if (!status.equals("RUNNING") && !status.equals("PENDING")) {
                assertEquals("SUCCEEDED", status, () -> "run failed: " + run.path("failureReason").asText()
                    + " steps=" + run.path("steps"));
                return run;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("run did not complete in time");
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}

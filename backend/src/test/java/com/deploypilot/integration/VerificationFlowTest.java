package com.deploypilot.integration;

import com.deploypilot.verify.MockDeploymentServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VerificationFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String register() throws Exception {
        String username = "verify" + System.nanoTime();
        MvcResult result = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "username", username, "email", username + "@example.com", "password", "password123"))))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
            .path("data").path("token").asText();
    }

    private long importProject(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/projects/import")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repository\":\"demo/sample-monorepo\"}"))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
            .path("data").path("projectId").asLong();
    }

    private JsonNode pollUntilComplete(String token, long projectId, long runId) throws Exception {
        for (int i = 0; i < 40; i++) {
            MvcResult result = mockMvc.perform(get("/projects/" + projectId + "/verifications/" + runId)
                    .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
            JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
            if (!data.path("completedAt").isNull() && !data.path("completedAt").asText().isEmpty()
                && !"RUNNING".equals(data.path("overallStatus").asText())) {
                return data;
            }
            Thread.sleep(250);
        }
        fail("Verification run did not complete in time");
        return null;
    }

    @Test
    void verifiesLocalMockFrontendEndToEnd() throws Exception {
        String token = register();
        long projectId = importProject(token);
        try (MockDeploymentServer fe = new MockDeploymentServer()) {
            fe.route("/", 200, "text/html", "<html><body>My deployed app</body></html>");
            fe.fallback(200, "text/html", "<html><body>My deployed app</body></html>");

            MvcResult started = mockMvc.perform(post("/projects/" + projectId + "/verifications")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
                        "frontendUrl", fe.baseUrl(),
                        "allowInsecureLocal", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallStatus").value("RUNNING"))
                .andReturn();
            long runId = objectMapper.readTree(started.getResponse().getContentAsString())
                .path("data").path("id").asLong();

            JsonNode done = pollUntilComplete(token, projectId, runId);
            assertNotEquals("RUNNING", done.path("overallStatus").asText());
            assertTrue(done.path("result").path("checks").size() > 0);
        }
    }

    @Test
    void rejectsSsrfTargetsAtTheApi() throws Exception {
        String token = register();
        long projectId = importProject(token);
        // metadata address, production mode -> blocked with 400
        mockMvc.perform(post("/projects/" + projectId + "/verifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"backendUrl\":\"http://169.254.169.254/latest/meta-data\"}"))
            .andExpect(status().isBadRequest());
        // plain http production URL -> blocked
        mockMvc.perform(post("/projects/" + projectId + "/verifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"frontendUrl\":\"http://example.com\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAtLeastOneUrl() throws Exception {
        String token = register();
        long projectId = importProject(token);
        mockMvc.perform(post("/projects/" + projectId + "/verifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void enforcesProjectOwnership() throws Exception {
        String owner = register();
        String intruder = register();
        long projectId = importProject(owner);
        mockMvc.perform(post("/projects/" + projectId + "/verifications")
                .header("Authorization", "Bearer " + intruder)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"frontendUrl\":\"https://example.com\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/projects/" + projectId + "/verifications")
                .header("Authorization", "Bearer " + intruder))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/projects/" + projectId + "/targets")
                .header("Authorization", "Bearer " + intruder))
            .andExpect(status().isForbidden());
    }

    @Test
    void preventsRapidDuplicateRuns() throws Exception {
        String token = register();
        long projectId = importProject(token);
        try (MockDeploymentServer fe = new MockDeploymentServer()) {
            fe.route("/", 200, "text/html", "<html>App</html>");
            fe.fallback(200, "text/html", "<html>App</html>");
            String body = objectMapper.writeValueAsString(Map.of(
                "frontendUrl", fe.baseUrl(), "allowInsecureLocal", true));
            mockMvc.perform(post("/projects/" + projectId + "/verifications")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
            // immediate second start hits the running-or-rate-limit guard -> 409
            mockMvc.perform(post("/projects/" + projectId + "/verifications")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        }
    }

    @Test
    void targetCrudEnforcesHttpsAndOwnership() throws Exception {
        String token = register();
        long projectId = importProject(token);
        // http production target rejected
        mockMvc.perform(post("/projects/" + projectId + "/targets")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"FRONTEND\",\"url\":\"http://example.com\"}"))
            .andExpect(status().isBadRequest());
        // https accepted
        mockMvc.perform(post("/projects/" + projectId + "/targets")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"FRONTEND\",\"url\":\"https://example.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.url").value("https://example.com"));
    }

    @Test
    void logSanitizeRedactsAndDoesNotPersistRaw() throws Exception {
        String token = register();
        long projectId = importProject(token);
        String secret = "ghp_" + "b".repeat(36);
        MvcResult result = mockMvc.perform(post("/projects/" + projectId + "/logs/sanitize")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "content", "deploy failed\nGITHUB_TOKEN=" + secret))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.warning").isNotEmpty())
            .andReturn();
        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains(secret), "raw secret must not be echoed back");
        // there is no log entity/table at all — nothing to persist by design
    }

    @Test
    void assistWorksWithoutAiConfigured() throws Exception {
        String token = register();
        long projectId = importProject(token);
        mockMvc.perform(post("/projects/" + projectId + "/assist")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"why is my deploy failing?\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contextSummary").isNotEmpty())
            .andExpect(jsonPath("$.data.aiAvailable").value(false));
    }
}

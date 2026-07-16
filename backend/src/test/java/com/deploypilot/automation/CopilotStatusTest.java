package com.deploypilot.automation;

import com.deploypilot.ai.AiProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Project Copilot and deterministic status against mock providers with a mocked
 * AI. Covers ownership, secret/token secrecy in prompts and responses, bounded
 * history, AI-failure resilience, deploy-proposes-plan-without-execution,
 * confirmation cannot be bypassed, deterministic status, and no false success
 * claims (tests 1–9, 21, 22).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CopilotStatusTest {

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
    @MockBean AiProvider ai;

    @BeforeEach
    void reset() {
        MOCK.reset();
        when(ai.isConfigured()).thenReturn(false);
        when(ai.generate(anyString())).thenReturn(AiProvider.AiResponse.unavailable("AI off"));
    }

    @Test
    void crossUserCopilotConversationsAreForbidden() throws Exception {
        String owner = register();
        long projectId = importRepo(owner);
        sendMessage(owner, projectId, "What is happening?");

        String attacker = register();
        mockMvc.perform(get("/projects/" + projectId + "/copilot/conversations/current")
                .header("Authorization", "Bearer " + attacker))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/projects/" + projectId + "/copilot/messages")
                .header("Authorization", "Bearer " + attacker)
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void providerTokensNeverAppearInResponses() throws Exception {
        String token = register();
        connect(token, "supabase", "sbp_topsecret_token_value");
        MvcResult result = mockMvc.perform(get("/connections").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("sbp_topsecret_token_value"));
    }

    @Test
    void crossUserSupabaseProjectsCannotBeListedWithoutOwnConnection() throws Exception {
        String user = register(); // no Supabase connection
        mockMvc.perform(get("/connections/supabase/projects").header("Authorization", "Bearer " + user))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void automationSecretsNeverEnterAiPrompts() throws Exception {
        when(ai.isConfigured()).thenReturn(true);
        when(ai.generate(anyString())).thenReturn(AiProvider.AiResponse.ok("ok"));
        String token = register();
        long projectId = importRepo(token);
        saveSecret(token, projectId, "DATABASE_URL", "postgresql://u:SUPERSECRETVALUE@h:5432/db");

        sendMessage(token, projectId, "Which variables are missing?");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(ai, atLeastOnce()).generate(prompt.capture());
        assertTrue(prompt.getAllValues().stream().noneMatch(p -> p.contains("SUPERSECRETVALUE")),
            "the secret value must never be sent to the AI");
    }

    @Test
    void copilotHistoryIsBounded() throws Exception {
        String token = register();
        long projectId = importRepo(token);
        for (int i = 0; i < 25; i++) sendMessage(token, projectId, "message " + i);
        JsonNode convo = getConversation(token, projectId);
        int count = convo.path("messages").size();
        assertTrue(count <= 40 && count > 0, "history should be bounded, was " + count);
    }

    @Test
    void aiFailureDoesNotBreakStatusOrCopilot() throws Exception {
        when(ai.isConfigured()).thenReturn(true);
        when(ai.generate(anyString())).thenReturn(AiProvider.AiResponse.unavailable("gemini timed out"));
        String token = register();
        long projectId = importRepo(token);

        JsonNode msg = sendMessage(token, projectId, "What do I need to do?");
        assertFalse(msg.path("content").asText().isBlank(), "deterministic answer must still be returned");
        mockMvc.perform(get("/projects/" + projectId + "/status").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    void deployRequestProposesPlanButPerformsNoExternalActionAndCannotBypassConfirmation() throws Exception {
        String token = register();
        connect(token, "github", "ghp_x");
        connect(token, "netlify", "nfp_x");
        connect(token, "render", "rnd_x");
        long projectId = importRepo(token);

        JsonNode msg = sendMessage(token, projectId, "Deploy this project.");
        assertEquals("DEPLOY", msg.path("proposedAction").path("type").asText());
        assertFalse(msg.path("proposedAction").path("planHash").asText().isBlank());
        // The proposed action carries no confirmation nonce and cannot execute.
        assertTrue(msg.path("proposedAction").path("nonce").isMissingNode());

        // No external provider mutation happened, and no run was created.
        assertEquals(0, MOCK.countExact("POST", "/nf/sites"));
        assertEquals(0, MOCK.countExact("POST", "/rd/services"));
        assertEquals(0, MOCK.countExact("POST", "/sb/projects"));
        MvcResult runs = mockMvc.perform(get("/projects/" + projectId + "/automation/runs")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn();
        assertEquals(0, data(runs).size(), "the Copilot must not create or execute a run");
    }

    @Test
    void statusIsDerivedFromRealRecordsWithoutAi() throws Exception {
        when(ai.isConfigured()).thenReturn(false);
        String token = register();
        long projectId = importRepo(token);
        MvcResult result = mockMvc.perform(get("/projects/" + projectId + "/status")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn();
        JsonNode status = data(result);
        assertTrue(milestoneDone(status, "repository_imported"));
        assertTrue(milestoneDone(status, "analysis_completed"));
        assertTrue(milestoneDone(status, "blueprint_generated"));
        assertTrue(status.path("aiExplanation").isNull() || status.path("aiExplanation").isMissingNode());
        assertFalse(status.path("summary").asText().isBlank());
    }

    @Test
    void copilotCannotClaimSuccessWithoutMatchingEvidence() throws Exception {
        when(ai.isConfigured()).thenReturn(true);
        when(ai.generate(anyString())).thenReturn(AiProvider.AiResponse.ok("Yes! Your deployment succeeded and is fully live!"));
        String token = register();
        long projectId = importRepo(token); // no deployment has run

        JsonNode msg = sendMessage(token, projectId, "Did my deployment succeed?");
        String content = msg.path("content").asText();
        // The authoritative, deterministic verified-facts must reflect reality.
        assertTrue(content.contains("No deployment has run yet"),
            "verified facts must state there is no evidence of a successful deployment");
    }

    // ==================== helpers ====================

    private String register() throws Exception {
        String username = "cop" + System.nanoTime();
        MvcResult result = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "username", username, "email", username + "@example.com", "password", "password123"))))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
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
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("projectId").asLong();
    }

    private void saveSecret(String token, long projectId, String name, String value) throws Exception {
        mockMvc.perform(put("/projects/" + projectId + "/automation/secrets")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", name, "value", value))))
            .andExpect(status().isOk());
    }

    private JsonNode sendMessage(String token, long projectId, String message) throws Exception {
        MvcResult result = mockMvc.perform(post("/projects/" + projectId + "/copilot/messages")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("message", message))))
            .andExpect(status().isOk()).andReturn();
        return data(result);
    }

    private JsonNode getConversation(String token, long projectId) throws Exception {
        MvcResult result = mockMvc.perform(get("/projects/" + projectId + "/copilot/conversations/current")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn();
        return data(result);
    }

    private boolean milestoneDone(JsonNode status, String key) {
        for (JsonNode m : status.path("milestones")) {
            if (key.equals(m.path("key").asText())) return m.path("done").asBoolean();
        }
        return false;
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}

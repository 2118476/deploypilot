package com.deploypilot.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
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

/** Per-user connection ownership: connections are private and disconnect works. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConnectionOwnershipTest {

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
    }

    @AfterAll
    static void stop() { MOCK.close(); }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void connectionsArePrivateToTheOwner() throws Exception {
        String alice = register();
        connect(alice, "github", "ghp_alice_token");

        // Alice sees GitHub connected.
        String aliceView = mockMvc.perform(get("/connections").header("Authorization", "Bearer " + alice))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(gitHubConnected(aliceView), "owner sees their connection");

        // Bob sees nothing connected and cannot use Alice's connection.
        String bob = register();
        String bobView = mockMvc.perform(get("/connections").header("Authorization", "Bearer " + bob))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(gitHubConnected(bobView), "another user must not see the owner's connection");

        // Bob has no GitHub connection, so listing repositories is not found for him.
        mockMvc.perform(get("/connections/github/repositories").header("Authorization", "Bearer " + bob))
            .andExpect(status().isNotFound());
    }

    @Test
    void disconnectRemovesTheConnection() throws Exception {
        String user = register();
        connect(user, "render", "rnd_token_value");
        mockMvc.perform(delete("/connections/render").header("Authorization", "Bearer " + user))
            .andExpect(status().isOk());
        String view = mockMvc.perform(get("/connections").header("Authorization", "Bearer " + user))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode connections = objectMapper.readTree(view).path("data");
        for (JsonNode c : connections) {
            if ("RENDER".equals(c.path("provider").asText())) {
                assertFalse(c.path("connected").asBoolean(), "connection should be gone after disconnect");
            }
        }
    }

    private boolean gitHubConnected(String responseBody) throws Exception {
        JsonNode connections = objectMapper.readTree(responseBody).path("data");
        for (JsonNode c : connections) {
            if ("GITHUB".equals(c.path("provider").asText())) return c.path("connected").asBoolean();
        }
        return false;
    }

    private String register() throws Exception {
        String username = "conn" + System.nanoTime();
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
}

package com.deploypilot.integration;

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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAndProjectFlowTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String uniqueName() {
        return "user" + System.nanoTime() + COUNTER.incrementAndGet();
    }

    /** Registers a fresh user and returns a valid JWT. */
    private String registerAndGetToken() throws Exception {
        String username = uniqueName();
        MvcResult result = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "username", username,
                    "email", username + "@example.com",
                    "password", "password123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.token").isNotEmpty())
            .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("token").asText();
    }

    private long createProject(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/projects")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", name))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
            .path("data").path("id").asLong();
    }

    // ---- registration and login ----

    @Test
    void registerAndLoginRoundTrip() throws Exception {
        String username = uniqueName();
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "username", username, "email", username + "@example.com", "password", "password123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token").isNotEmpty());

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "username", username, "password", "password123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    void loginWithWrongPasswordFails() throws Exception {
        String username = uniqueName();
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "username", username, "email", username + "@example.com", "password", "password123"))))
            .andExpect(status().isOk());

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "username", username, "password", "wrong-password"))))
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void registrationRejectsInvalidInput() throws Exception {
        // bad email + short password -> bean validation 400
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "username", "ab", "email", "not-an-email", "password", "123"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void duplicateUsernameIsRejected() throws Exception {
        String username = uniqueName();
        Map<String, String> request = Map.of(
            "username", username, "email", username + "@example.com", "password", "password123");
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(jsonPath("$.success").value(false));
    }

    // ---- protected endpoints ----

    @Test
    void protectedEndpointsRejectMissingToken() throws Exception {
        mockMvc.perform(get("/projects")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/dashboard")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/projects/1/analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repository\":\"demo/sample-monorepo\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsRejectGarbageToken() throws Exception {
        mockMvc.perform(get("/projects").header("Authorization", "Bearer not.a.jwt"))
            .andExpect(status().isUnauthorized());
    }

    // ---- projects and ownership ----

    @Test
    void createProjectAndReadItBack() throws Exception {
        String token = registerAndGetToken();
        long id = createProject(token, "My App");
        mockMvc.perform(get("/projects/" + id).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("My App"));
    }

    @Test
    void projectCreationValidatesInput() throws Exception {
        String token = registerAndGetToken();
        mockMvc.perform(post("/projects")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", ""))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void usersCannotAccessOthersProjects() throws Exception {
        String tokenA = registerAndGetToken();
        String tokenB = registerAndGetToken();
        long projectA = createProject(tokenA, "Owned by A");

        mockMvc.perform(get("/projects/" + projectA).header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/projects/" + projectA + "/env-vars").header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/projects/" + projectA + "/analysis")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repository\":\"demo/sample-monorepo\"}"))
            .andExpect(status().isForbidden());
    }

    // ---- deployment plan generation ----

    @Test
    void generatesDeploymentPlanForOwnedProject() throws Exception {
        String token = registerAndGetToken();
        long id = createProject(token, "Plan Me");
        mockMvc.perform(post("/projects/" + id + "/generate-plan")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "projectType", "fullstack",
                    "frontendTech", "react-vite",
                    "backendTech", "spring-boot",
                    "database", "supabase-postgresql",
                    "frontendHost", "netlify",
                    "backendHost", "render"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.steps").isArray())
            .andExpect(jsonPath("$.data.steps[0]").exists());
    }

    // ---- input-size limits ----

    @Test
    void troubleshootRejectsOversizedContent() throws Exception {
        String token = registerAndGetToken();
        String huge = "x".repeat(20001);
        mockMvc.perform(post("/troubleshoot")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("errorType", "BUILD", "content", huge))))
            .andExpect(status().isBadRequest());
    }
}

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end analysis flow against the bundled fixture repository
 * (deploypilot.repo-access.mode=fixture in the test profile — no network).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RepositoryAnalysisFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String token;

    private String token() throws Exception {
        if (token != null) return token;
        String username = "analyst" + System.nanoTime();
        MvcResult result = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "username", username,
                    "email", username + "@example.com",
                    "password", "password123"))))
            .andExpect(status().isOk()).andReturn();
        token = objectMapper.readTree(result.getResponse().getContentAsString())
            .path("data").path("token").asText();
        return token;
    }

    private long createProject() throws Exception {
        MvcResult result = mockMvc.perform(post("/projects")
                .header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Analysis Target"))))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
            .path("data").path("id").asLong();
    }

    @Test
    void analyzesFixtureMonorepoEndToEnd() throws Exception {
        long projectId = createProject();

        MvcResult result = mockMvc.perform(post("/projects/" + projectId + "/analysis")
                .header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repository\":\"demo/sample-monorepo\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andReturn();

        JsonNode analysis = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        JsonNode detection = analysis.path("result");

        assertEquals("MONOREPO", detection.path("structure").asText());

        List<String> names = new ArrayList<>();
        for (JsonNode d : detection.path("detections")) {
            names.add(d.path("category").asText() + ":" + d.path("name").asText());
        }
        assertTrue(names.contains("FRONTEND_FRAMEWORK:React"), "React not detected: " + names);
        assertTrue(names.contains("BACKEND_FRAMEWORK:Spring Boot"), "Spring Boot not detected: " + names);
        assertTrue(names.contains("LANGUAGE:Java 17"), "Java 17 not detected: " + names);
        assertTrue(names.contains("DATABASE:PostgreSQL"), "PostgreSQL not detected: " + names);
        assertTrue(names.contains("HOSTING:Netlify"), "Netlify not detected: " + names);
        assertTrue(names.contains("HOSTING:Render"), "Render not detected: " + names);
        assertTrue(names.contains("CONTAINER:Docker"), "Docker not detected: " + names);
        assertTrue(names.contains("PACKAGE_MANAGER:npm"), "npm not detected: " + names);

        // env var names present, classified, and never any values
        List<String> envNames = new ArrayList<>();
        for (JsonNode v : detection.path("environmentVariables")) {
            envNames.add(v.path("name").asText());
            assertFalse(v.has("value"));
        }
        assertTrue(envNames.contains("DATABASE_URL"));
        assertTrue(envNames.contains("VITE_API_BASE_URL"));
        String rawBody = result.getResponse().getContentAsString();
        assertFalse(rawBody.contains("change-me"), "secret-looking fixture values must not be returned");

        // every detection carries evidence
        for (JsonNode d : detection.path("detections")) {
            assertTrue(d.path("evidence").size() > 0,
                "detection without evidence: " + d.path("name").asText());
            assertTrue(d.path("confidence").asText().matches("HIGH|MEDIUM|LOW"));
        }
    }

    @Test
    void latestAnalysisIsRetrievableAndRerunnable() throws Exception {
        long projectId = createProject();

        mockMvc.perform(post("/projects/" + projectId + "/analysis")
                .header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repository\":\"demo/sample-monorepo\"}"))
            .andExpect(status().isOk());

        // re-run (idempotent, new record) then read latest
        mockMvc.perform(post("/projects/" + projectId + "/analysis")
                .header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repository\":\"demo/sample-monorepo\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/projects/" + projectId + "/analysis")
                .header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.repository").value("demo/sample-monorepo"));
    }

    @Test
    void unknownRepositoryReturnsNotFoundAndPersistsFailure() throws Exception {
        long projectId = createProject();

        mockMvc.perform(post("/projects/" + projectId + "/analysis")
                .header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repository\":\"nobody/does-not-exist\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));

        // the failed attempt is recorded and visible as the latest analysis
        mockMvc.perform(get("/projects/" + projectId + "/analysis")
                .header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.errorMessage").isNotEmpty());
    }

    @Test
    void invalidRepositoryInputIsRejected() throws Exception {
        long projectId = createProject();
        mockMvc.perform(post("/projects/" + projectId + "/analysis")
                .header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repository\":\"not a repository !!\"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/projects/" + projectId + "/analysis")
                .header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repository\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void analysisForUnknownProjectIs404() throws Exception {
        mockMvc.perform(get("/projects/999999/analysis")
                .header("Authorization", "Bearer " + token()))
            .andExpect(status().isNotFound());
    }
}

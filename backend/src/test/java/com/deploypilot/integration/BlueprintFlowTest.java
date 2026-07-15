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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Stage 2 end-to-end flow against the bundled fixture repository:
 * import -> project + analysis + blueprint, overrides, staleness, ownership.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BlueprintFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String register() throws Exception {
        String username = "bp" + System.nanoTime();
        MvcResult result = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "username", username, "email", username + "@example.com", "password", "password123"))))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
            .path("data").path("token").asText();
    }

    private JsonNode importRepo(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/projects/import")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repository\":\"demo/sample-monorepo\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    @Test
    void importCreatesProjectAnalysisAndBlueprint() throws Exception {
        String token = register();
        JsonNode data = importRepo(token);

        long projectId = data.path("projectId").asLong();
        assertTrue(projectId > 0);
        assertEquals("sample-monorepo", data.path("projectName").asText());
        assertEquals("COMPLETED", data.path("analysis").path("status").asText());

        JsonNode blueprint = data.path("blueprint").path("result");
        assertEquals("MONOREPO", blueprint.path("structure").asText());
        assertTrue(blueprint.path("components").size() >= 3, "frontend, backend and database expected");

        // project is readable and carries the repository URL
        mockMvc.perform(get("/projects/" + projectId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.githubUrl").value("https://github.com/demo/sample-monorepo"));

        // blueprint retrievable and not stale
        mockMvc.perform(get("/projects/" + projectId + "/blueprint").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stale").value(false))
            .andExpect(jsonPath("$.data.result.steps").isArray());
    }

    @Test
    void importOfUnknownRepositoryCreatesNoProject() throws Exception {
        String token = register();
        String before = mockMvc.perform(get("/projects").header("Authorization", "Bearer " + token))
            .andReturn().getResponse().getContentAsString();
        int countBefore = objectMapper.readTree(before).path("data").size();

        mockMvc.perform(post("/projects/import")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repository\":\"nobody/missing-repo\"}"))
            .andExpect(status().isNotFound());

        String after = mockMvc.perform(get("/projects").header("Authorization", "Bearer " + token))
            .andReturn().getResponse().getContentAsString();
        assertEquals(countBefore, objectMapper.readTree(after).path("data").size());
    }

    @Test
    void previewAnalysisPersistsNothing() throws Exception {
        String token = register();
        mockMvc.perform(post("/repositories/preview-analysis")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repository\":\"demo/sample-monorepo\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.detections").isArray())
            .andExpect(jsonPath("$.data.structure").value("MONOREPO"));

        String projects = mockMvc.perform(get("/projects").header("Authorization", "Bearer " + token))
            .andReturn().getResponse().getContentAsString();
        assertEquals(0, objectMapper.readTree(projects).path("data").size(),
            "preview must not create a project");
    }

    @Test
    void overridesRecalculateAndRejectIncompatiblePlatforms() throws Exception {
        String token = register();
        long projectId = importRepo(token).path("projectId").asLong();

        MvcResult result = mockMvc.perform(put("/projects/" + projectId + "/blueprint/overrides")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"frontend@frontend\":\"Vercel\"}"))
            .andExpect(status().isOk()).andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        JsonNode frontend = null;
        for (JsonNode c : data.path("result").path("components")) {
            if ("frontend@frontend".equals(c.path("id").asText())) frontend = c;
        }
        assertNotNull(frontend);
        assertEquals("Vercel", frontend.path("selectedPlatform").asText());
        assertEquals("Netlify", frontend.path("recommendedPlatform").path("platform").asText());
        assertEquals("Vercel", data.path("overrides").path("frontend@frontend").asText());

        // incompatible platform must be rejected, not silently accepted
        mockMvc.perform(put("/projects/" + projectId + "/blueprint/overrides")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"backend@backend\":\"Vercel\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void newAnalysisMarksBlueprintStaleUntilRegenerated() throws Exception {
        String token = register();
        long projectId = importRepo(token).path("projectId").asLong();

        // a fresh analysis supersedes the one the blueprint was built from
        mockMvc.perform(post("/projects/" + projectId + "/analysis")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repository\":\"demo/sample-monorepo\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/projects/" + projectId + "/blueprint").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stale").value(true));

        mockMvc.perform(post("/projects/" + projectId + "/blueprint").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stale").value(false));
    }

    @Test
    void crossUserBlueprintAccessRejected() throws Exception {
        String tokenA = register();
        String tokenB = register();
        long projectA = importRepo(tokenA).path("projectId").asLong();

        mockMvc.perform(get("/projects/" + projectA + "/blueprint").header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/projects/" + projectA + "/blueprint").header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/projects/" + projectA + "/blueprint/overrides")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"frontend@frontend\":\"Vercel\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void blueprintNeverContainsSecretValues() throws Exception {
        String token = register();
        long projectId = importRepo(token).path("projectId").asLong();
        String body = mockMvc.perform(get("/projects/" + projectId + "/blueprint")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        // fixture .env.example uses "change-me" as its placeholder secret value
        assertFalse(body.contains("change-me"), "secret-looking values must never appear in blueprints");
    }

    @Test
    void blueprintWithoutAnalysisIsRejected() throws Exception {
        String token = register();
        MvcResult created = mockMvc.perform(post("/projects")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Manual project"))))
            .andExpect(status().isOk()).andReturn();
        long projectId = objectMapper.readTree(created.getResponse().getContentAsString())
            .path("data").path("id").asLong();

        mockMvc.perform(post("/projects/" + projectId + "/blueprint").header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest());
    }
}

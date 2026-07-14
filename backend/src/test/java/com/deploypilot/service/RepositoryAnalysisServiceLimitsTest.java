package com.deploypilot.service;

import com.deploypilot.repository.ProjectRepository;
import com.deploypilot.repository.RepositoryAnalysisRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests for the file-selection safety rules (Part F limits). */
class RepositoryAnalysisServiceLimitsTest {

    private final RepositoryAnalysisService service = new RepositoryAnalysisService(
        null, new StackDetectionService(),
        Mockito.mock(RepositoryAnalysisRepository.class),
        Mockito.mock(ProjectRepository.class),
        new ObjectMapper());

    @Test
    void acceptsKnownConfigurationFiles() {
        assertTrue(service.isInteresting("package.json"));
        assertTrue(service.isInteresting("frontend/package.json"));
        assertTrue(service.isInteresting("backend/pom.xml"));
        assertTrue(service.isInteresting("backend/src/main/resources/application.yml"));
        assertTrue(service.isInteresting(".env.example"));
        assertTrue(service.isInteresting("vite.config.ts"));
        assertTrue(service.isInteresting("Dockerfile"));
        assertTrue(service.isInteresting("render.yaml"));
    }

    @Test
    void refusesRealEnvFiles() {
        assertFalse(service.isInteresting(".env"));
        assertFalse(service.isInteresting(".env.local"));
        assertFalse(service.isInteresting(".env.production"));
        assertFalse(service.isInteresting("backend/.env"));
    }

    @Test
    void refusesDependencyAndBuildDirectories() {
        assertFalse(service.isInteresting("node_modules/react/package.json"));
        assertFalse(service.isInteresting("frontend/node_modules/vite/package.json"));
        assertFalse(service.isInteresting("backend/target/classes/application.yml"));
        assertFalse(service.isInteresting("vendor/lib/package.json"));
        assertFalse(service.isInteresting("frontend/dist/package.json"));
        assertFalse(service.isInteresting(".git/config"));
    }

    @Test
    void refusesUninterestingAndDeepFiles() {
        assertFalse(service.isInteresting("src/App.tsx"));
        assertFalse(service.isInteresting("logo.png"));
        assertFalse(service.isInteresting("a/b/c/d/e/f/g/package.json"));
    }
}

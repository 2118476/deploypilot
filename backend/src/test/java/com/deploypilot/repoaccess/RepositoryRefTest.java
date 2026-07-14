package com.deploypilot.repoaccess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryRefTest {

    @Test
    void parsesOwnerSlashName() {
        RepositoryRef ref = RepositoryRef.parse("octocat/hello-world");
        assertEquals("octocat", ref.owner());
        assertEquals("hello-world", ref.name());
        assertEquals("octocat/hello-world", ref.fullName());
    }

    @Test
    void parsesGitHubUrls() {
        assertEquals("octocat/hello-world",
            RepositoryRef.parse("https://github.com/octocat/hello-world").fullName());
        assertEquals("octocat/hello-world",
            RepositoryRef.parse("https://github.com/octocat/hello-world.git").fullName());
        assertEquals("octocat/hello-world",
            RepositoryRef.parse("github.com/octocat/hello-world/").fullName());
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> RepositoryRef.parse(null));
        assertThrows(IllegalArgumentException.class, () -> RepositoryRef.parse(""));
        assertThrows(IllegalArgumentException.class, () -> RepositoryRef.parse("not a repo"));
        assertThrows(IllegalArgumentException.class, () -> RepositoryRef.parse("https://gitlab.com/a/b"));
        assertThrows(IllegalArgumentException.class, () -> RepositoryRef.parse("a/b/c"));
    }
}

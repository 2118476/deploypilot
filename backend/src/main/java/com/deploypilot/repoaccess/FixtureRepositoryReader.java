package com.deploypilot.repoaccess;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Development/test adapter that serves repositories from classpath fixtures
 * under fixtures/repos/{owner}/{name}/. Lets the analysis flow be exercised
 * end to end without network access or GitHub credentials. Selected with
 * deploypilot.repo-access.mode=fixture; never used unless explicitly enabled.
 */
public class FixtureRepositoryReader implements RepositoryFileReader {

    private static final String ROOT = "fixtures/repos/";

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    @Override
    public RepositoryMetadata fetchMetadata(RepositoryRef ref) {
        if (listFiles(ref, "main").entries().isEmpty()) {
            throw new RepositoryAccessException.NotFound(
                "Fixture repository not found: " + ref.fullName());
        }
        return new RepositoryMetadata(ref.fullName(), "main", false);
    }

    @Override
    public FileListing listFiles(RepositoryRef ref, String branch) {
        String prefix = ROOT + ref.owner() + "/" + ref.name() + "/";
        List<RepositoryFileEntry> entries = new ArrayList<>();
        try {
            Resource[] resources = resolver.getResources("classpath*:" + prefix + "**");
            for (Resource resource : resources) {
                if (!resource.isReadable()) continue;
                String url = resource.getURL().toString();
                int idx = url.indexOf(prefix);
                if (idx < 0) continue;
                String path = url.substring(idx + prefix.length());
                if (path.isEmpty() || path.endsWith("/")) continue;
                entries.add(new RepositoryFileEntry(path, resource.contentLength()));
            }
        } catch (IOException e) {
            throw new RepositoryAccessException("Could not list fixture repository " + ref.fullName(), e);
        }
        return new FileListing(entries, false);
    }

    @Override
    public String readTextFile(RepositoryRef ref, String branch, String path, int maxBytes) {
        String location = ROOT + ref.owner() + "/" + ref.name() + "/" + path;
        try (InputStream in = resolver.getResource("classpath:" + location).getInputStream()) {
            byte[] bytes = in.readNBytes(maxBytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RepositoryAccessException.NotFound("Fixture file not found: " + location);
        }
    }
}

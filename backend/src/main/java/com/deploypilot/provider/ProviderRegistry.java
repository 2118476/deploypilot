package com.deploypilot.provider;

import com.deploypilot.model.enums.ProviderType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Resolves the adapter for a given provider. */
@Component
public class ProviderRegistry {

    private final GitProvider gitProvider;
    private final Map<ProviderType, HostingProvider> hostingByType = new EnumMap<>(ProviderType.class);

    public ProviderRegistry(GitProvider gitProvider, List<HostingProvider> hostingProviders) {
        this.gitProvider = gitProvider;
        for (HostingProvider p : hostingProviders) {
            hostingByType.put(p.type(), p);
        }
    }

    public GitProvider git() { return gitProvider; }

    public HostingProvider hosting(ProviderType type) {
        HostingProvider p = hostingByType.get(type);
        if (p == null) {
            throw new ProviderException("No hosting adapter for provider " + type);
        }
        return p;
    }

    public boolean isHosting(ProviderType type) {
        return hostingByType.containsKey(type);
    }
}

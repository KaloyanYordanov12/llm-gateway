package dev.kaloyanyordanov.llmgateway.provider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry of available {@link ProviderClient}s, keyed by name, with a configured
 * default. Supports N providers so v2 failover/routing can drop in without
 * changing call sites.
 */
public class ProviderRegistry {

    private final Map<String, ProviderClient> providersByName = new LinkedHashMap<>();
    private final String defaultProviderName;

    /**
     * @param providers           all registered provider clients
     * @param defaultProviderName the name to serve from {@link #getDefault()}
     */
    public ProviderRegistry(List<ProviderClient> providers, String defaultProviderName) {
        for (ProviderClient provider : providers) {
            providersByName.put(provider.name(), provider);
        }
        this.defaultProviderName = defaultProviderName;
    }

    /**
     * @param name provider name
     * @return the named provider
     * @throws IllegalArgumentException if no provider with that name is registered
     */
    public ProviderClient get(String name) {
        ProviderClient provider = providersByName.get(name);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown provider: " + name);
        }
        return provider;
    }

    /**
     * @return the configured default provider
     */
    public ProviderClient getDefault() {
        return get(defaultProviderName);
    }

    /**
     * @return the names of all registered providers
     */
    public Set<String> names() {
        return Set.copyOf(providersByName.keySet());
    }
}

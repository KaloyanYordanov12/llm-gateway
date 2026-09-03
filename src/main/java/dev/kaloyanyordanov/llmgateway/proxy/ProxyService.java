package dev.kaloyanyordanov.llmgateway.proxy;

import dev.kaloyanyordanov.llmgateway.cache.ResponseCacheService;
import dev.kaloyanyordanov.llmgateway.provider.ProviderRegistry;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a proxied request: serve from cache when possible, otherwise call
 * the provider and cache the result. Usage accounting (Phase 5) layers in here.
 */
@Service
public class ProxyService {

    private final ProviderRegistry providerRegistry;
    private final ResponseCacheService cache;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
            justification = "Collaborators are Spring-managed singletons; holding the shared "
                    + "references is intentional DI, not mutable-state exposure.")
    public ProxyService(ProviderRegistry providerRegistry, ResponseCacheService cache) {
        this.providerRegistry = providerRegistry;
        this.cache = cache;
    }

    /**
     * Handles a messages request, returning a cached completion on a cache hit or
     * a fresh provider completion (which is then cached) on a miss.
     *
     * @param request the inbound request
     * @return the response to return to the client
     */
    public MessagesResponse handle(MessagesRequest request) {
        Optional<MessagesResponse> cached = cache.get(request);
        if (cached.isPresent()) {
            return cached.get();
        }
        MessagesResponse response = providerRegistry.getDefault().createMessage(request);
        cache.put(request, response);
        return response;
    }
}

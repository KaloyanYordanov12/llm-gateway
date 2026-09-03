package dev.kaloyanyordanov.llmgateway.proxy;

import dev.kaloyanyordanov.llmgateway.cache.ResponseCacheService;
import dev.kaloyanyordanov.llmgateway.provider.ProviderRegistry;
import dev.kaloyanyordanov.llmgateway.usage.PricingService;
import dev.kaloyanyordanov.llmgateway.usage.UnknownModelException;
import dev.kaloyanyordanov.llmgateway.usage.UsageService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a proxied request: validate the model against the pricing
 * allowlist, serve from cache when possible, otherwise call the provider, record
 * usage, and cache the result. Cache hits are free (no provider cost, no usage
 * record), so totals never double-count.
 */
@Service
public class ProxyService {

    private final ProviderRegistry providerRegistry;
    private final ResponseCacheService cache;
    private final PricingService pricingService;
    private final UsageService usageService;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
            justification = "Collaborators are Spring-managed singletons; holding the shared "
                    + "references is intentional DI, not mutable-state exposure.")
    public ProxyService(ProviderRegistry providerRegistry, ResponseCacheService cache,
            PricingService pricingService, UsageService usageService) {
        this.providerRegistry = providerRegistry;
        this.cache = cache;
        this.pricingService = pricingService;
        this.usageService = usageService;
    }

    /**
     * Handles a messages request for a client.
     *
     * @param request  the inbound request
     * @param clientId the authenticated client's id
     * @return a cached completion on a hit, or a fresh provider completion on a miss
     * @throws UnknownModelException if the model is not in the pricing allowlist
     */
    public MessagesResponse handle(MessagesRequest request, Long clientId) {
        if (!pricingService.isKnown(request.model())) {
            throw new UnknownModelException(request.model());
        }
        Optional<MessagesResponse> cached = cache.get(request);
        if (cached.isPresent()) {
            return cached.get();
        }
        MessagesResponse response = providerRegistry.getDefault().createMessage(request);
        recordUsage(clientId, request.model(), response);
        cache.put(request, response);
        return response;
    }

    private void recordUsage(Long clientId, String model, MessagesResponse response) {
        Usage usage = response.usage();
        int inputTokens = usage != null && usage.inputTokens() != null ? usage.inputTokens() : 0;
        int outputTokens = usage != null && usage.outputTokens() != null ? usage.outputTokens() : 0;
        BigDecimal cost = pricingService.cost(model, inputTokens, outputTokens);
        usageService.record(clientId, model, inputTokens, outputTokens, cost);
    }
}

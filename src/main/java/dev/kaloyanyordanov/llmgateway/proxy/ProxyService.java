package dev.kaloyanyordanov.llmgateway.proxy;

import dev.kaloyanyordanov.llmgateway.cache.ResponseCacheService;
import dev.kaloyanyordanov.llmgateway.metrics.GatewayMetrics;
import dev.kaloyanyordanov.llmgateway.provider.routing.ProviderRouter;
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

    private final ProviderRouter providerRouter;
    private final ResponseCacheService cache;
    private final PricingService pricingService;
    private final UsageService usageService;
    private final GatewayMetrics metrics;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
            justification = "Collaborators are Spring-managed singletons; holding the shared "
                    + "references is intentional DI, not mutable-state exposure.")
    public ProxyService(ProviderRouter providerRouter, ResponseCacheService cache,
            PricingService pricingService, UsageService usageService, GatewayMetrics metrics) {
        this.providerRouter = providerRouter;
        this.cache = cache;
        this.pricingService = pricingService;
        this.usageService = usageService;
        this.metrics = metrics;
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
        metrics.providerCall();
        MessagesResponse response = providerRouter.route(request);
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

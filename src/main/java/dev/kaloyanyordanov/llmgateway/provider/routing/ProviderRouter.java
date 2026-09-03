package dev.kaloyanyordanov.llmgateway.provider.routing;

import dev.kaloyanyordanov.llmgateway.provider.AllProvidersUnavailableException;
import dev.kaloyanyordanov.llmgateway.provider.ProviderRegistry;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesRequest;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Executes per-model routing. A request whose model has no rule is sent to the
 * registry default (v1 behavior, unchanged). A routed request is rewritten with
 * the concrete upstream model and sent to the resolved provider, trying the rule's
 * targets in order and failing over on availability errors; if every target is
 * unavailable, {@link AllProvidersUnavailableException} is thrown (503).
 *
 * <p>Billing stays on the client's logical model (validated against the pricing
 * allowlist upstream in {@code ProxyService}), so routing cannot smuggle an
 * unpriced model through.</p>
 */
@Service
public class ProviderRouter {

    private final ModelRouter modelRouter;
    private final ProviderRegistry providerRegistry;

    public ProviderRouter(ModelRouter modelRouter, ProviderRegistry providerRegistry) {
        this.modelRouter = modelRouter;
        this.providerRegistry = providerRegistry;
    }

    /**
     * Fail-secure startup check: every provider referenced by a routing rule must
     * be a registered provider, or the context fails to start.
     */
    @PostConstruct
    void validateReferencedProviders() {
        Set<String> known = providerRegistry.names();
        for (String provider : modelRouter.referencedProviders()) {
            if (!known.contains(provider)) {
                throw new IllegalStateException("Unknown provider in routing rules: " + provider);
            }
        }
    }

    /**
     * Routes and invokes a request.
     *
     * @param request the inbound request (its {@code model} is the logical model)
     * @return the provider response
     */
    public MessagesResponse route(MessagesRequest request) {
        Optional<List<RouteTarget>> targets = modelRouter.resolve(request.model());
        if (targets.isEmpty()) {
            return providerRegistry.getDefault().createMessage(request);
        }
        RuntimeException lastFailure = null;
        for (RouteTarget target : targets.get()) {
            try {
                return providerRegistry.get(target.provider())
                        .createMessage(withModel(request, target.model()));
            } catch (CallNotPermittedException | HttpServerErrorException | ResourceAccessException failure) {
                // Availability failures warrant trying the next target; a 4xx propagates.
                lastFailure = failure;
            }
        }
        throw new AllProvidersUnavailableException(lastFailure);
    }

    private static MessagesRequest withModel(MessagesRequest request, String model) {
        return new MessagesRequest(model, request.system(), request.messages(),
                request.temperature(), request.topP(), request.maxTokens(), request.stopSequences());
    }
}

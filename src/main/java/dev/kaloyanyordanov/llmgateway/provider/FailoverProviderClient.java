package dev.kaloyanyordanov.llmgateway.provider;

import dev.kaloyanyordanov.llmgateway.proxy.MessagesRequest;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.util.List;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * A {@link ProviderClient} that fails over across an ordered chain of (already
 * resilience-wrapped) providers. When a provider's circuit is open, or it fails
 * after retries with a server error or transport error, the next provider in the
 * chain is tried — transparently to the caller. If every provider is unavailable,
 * {@link AllProvidersUnavailableException} is thrown (mapped to {@code 503}).
 *
 * <p>Only availability failures (circuit-open, 5xx, transport) trigger failover;
 * a client error (4xx) is the caller's fault and propagates unchanged, since a
 * second provider would reject it too.</p>
 */
public final class FailoverProviderClient implements ProviderClient {

    /** Name under which the failover chain is registered as the default. */
    public static final String FAILOVER_NAME = "failover";

    private final List<ProviderClient> chain;

    /**
     * @param chain the ordered provider chain (must be non-empty)
     */
    public FailoverProviderClient(List<ProviderClient> chain) {
        if (chain.isEmpty()) {
            throw new IllegalArgumentException("Failover chain must not be empty");
        }
        this.chain = List.copyOf(chain);
    }

    @Override
    public String name() {
        return FAILOVER_NAME;
    }

    @Override
    public MessagesResponse createMessage(MessagesRequest request) {
        RuntimeException lastFailure = null;
        for (ProviderClient provider : chain) {
            try {
                return provider.createMessage(request);
            } catch (CallNotPermittedException | HttpServerErrorException | ResourceAccessException failure) {
                // Availability failures warrant failover; a 4xx is not caught here and propagates.
                lastFailure = failure;
            }
        }
        throw new AllProvidersUnavailableException(lastFailure);
    }
}

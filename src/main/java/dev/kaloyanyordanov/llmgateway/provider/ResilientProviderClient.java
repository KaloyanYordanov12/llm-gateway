package dev.kaloyanyordanov.llmgateway.provider;

import dev.kaloyanyordanov.llmgateway.proxy.MessagesRequest;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.util.function.Supplier;

/**
 * Wraps a delegate {@link ProviderClient} with resilience: transient failures are
 * retried, and a circuit breaker trips after sustained failure to fail fast
 * instead of hammering an unhealthy provider.
 *
 * <p>Decoration order is circuit-breaker-outside, retry-inside: the breaker
 * records one outcome per logical call (after retries), and when open it rejects
 * calls immediately without invoking the delegate.</p>
 */
public class ResilientProviderClient implements ProviderClient {

    private final ProviderClient delegate;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    /**
     * @param delegate       the underlying provider client
     * @param circuitBreaker the circuit breaker guarding the provider
     * @param retry          the retry policy for transient failures
     */
    public ResilientProviderClient(ProviderClient delegate, CircuitBreaker circuitBreaker, Retry retry) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public MessagesResponse createMessage(MessagesRequest request) {
        Supplier<MessagesResponse> call = () -> delegate.createMessage(request);
        Supplier<MessagesResponse> resilient =
                CircuitBreaker.decorateSupplier(circuitBreaker, Retry.decorateSupplier(retry, call));
        return resilient.get();
    }
}

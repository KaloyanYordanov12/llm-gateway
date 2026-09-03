package dev.kaloyanyordanov.llmgateway.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.kaloyanyordanov.llmgateway.proxy.Message;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesRequest;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesResponse;
import dev.kaloyanyordanov.llmgateway.proxy.Usage;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResilientProviderClientTest {

    private static final MessagesRequest REQUEST =
            new MessagesRequest("m", null, List.of(new Message("user", "hi")), null, null, 10, null);
    private static final MessagesResponse RESPONSE =
            new MessagesResponse("id", "message", "assistant", "m", "hi", "end_turn", new Usage(1, 1));

    private static Retry retry(int maxAttempts) {
        return Retry.of("test", RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .waitDuration(Duration.ofMillis(1))
                .retryExceptions(RuntimeException.class)
                .build());
    }

    private static CircuitBreaker neverOpeningBreaker() {
        return CircuitBreaker.ofDefaults("test");
    }

    private static CircuitBreaker breakerOpeningAfter(int calls) {
        return CircuitBreaker.of("test", CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(calls)
                .minimumNumberOfCalls(calls)
                .failureRateThreshold(50f)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .permittedNumberOfCallsInHalfOpenState(2)
                .automaticTransitionFromOpenToHalfOpenEnabled(false)
                .build());
    }

    @Test
    void nameDelegates() {
        ProviderClient delegate = mock(ProviderClient.class);
        when(delegate.name()).thenReturn("anthropic");

        ResilientProviderClient client =
                new ResilientProviderClient(delegate, neverOpeningBreaker(), retry(1));

        assertThat(client.name()).isEqualTo("anthropic");
    }

    @Test
    void passesThroughOnSuccess() {
        ProviderClient delegate = mock(ProviderClient.class);
        when(delegate.createMessage(any())).thenReturn(RESPONSE);

        ResilientProviderClient client =
                new ResilientProviderClient(delegate, neverOpeningBreaker(), retry(3));

        assertThat(client.createMessage(REQUEST)).isSameAs(RESPONSE);
        verify(delegate, times(1)).createMessage(any());
    }

    @Test
    void retriesTransientFailureThenSucceeds() {
        ProviderClient delegate = mock(ProviderClient.class);
        when(delegate.createMessage(any()))
                .thenThrow(new RuntimeException("transient"))
                .thenReturn(RESPONSE);

        ResilientProviderClient client =
                new ResilientProviderClient(delegate, neverOpeningBreaker(), retry(3));

        assertThat(client.createMessage(REQUEST)).isSameAs(RESPONSE);
        verify(delegate, times(2)).createMessage(any());
    }

    @Test
    void throwsAfterRetryExhaustion() {
        ProviderClient delegate = mock(ProviderClient.class);
        when(delegate.createMessage(any())).thenThrow(new RuntimeException("down"));

        ResilientProviderClient client =
                new ResilientProviderClient(delegate, neverOpeningBreaker(), retry(3));

        assertThatThrownBy(() -> client.createMessage(REQUEST)).isInstanceOf(RuntimeException.class);
        verify(delegate, times(3)).createMessage(any());
    }

    @Test
    void circuitClosedToOpenToHalfOpenToClosed() {
        ProviderClient delegate = mock(ProviderClient.class);
        CircuitBreaker breaker = breakerOpeningAfter(2);
        ResilientProviderClient client = new ResilientProviderClient(delegate, breaker, retry(1));

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // Two failures fill the window and trip the breaker.
        when(delegate.createMessage(any())).thenThrow(new RuntimeException("down"));
        assertThatThrownBy(() -> client.createMessage(REQUEST)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> client.createMessage(REQUEST)).isInstanceOf(RuntimeException.class);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // While open, calls fail fast without touching the delegate.
        assertThatThrownBy(() -> client.createMessage(REQUEST))
                .isInstanceOf(CallNotPermittedException.class);
        verify(delegate, times(2)).createMessage(any());

        // Half-open: successful trial calls close the breaker again.
        breaker.transitionToHalfOpenState();
        doReturn(RESPONSE).when(delegate).createMessage(any());
        client.createMessage(REQUEST);
        client.createMessage(REQUEST);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}

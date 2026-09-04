package dev.kaloyanyordanov.llmgateway.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.kaloyanyordanov.llmgateway.proxy.Message;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesRequest;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesResponse;
import dev.kaloyanyordanov.llmgateway.proxy.Usage;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

class FailoverProviderClientTest {

    private static final MessagesRequest REQUEST =
            new MessagesRequest("m", null, List.of(new Message("user", "hi")), null, null, 10, null);
    private static final MessagesResponse PRIMARY =
            new MessagesResponse("p", "message", "assistant", "m", "primary", "end_turn", new Usage(1, 1));
    private static final MessagesResponse SECONDARY =
            new MessagesResponse("s", "message", "assistant", "m", "secondary", "end_turn", new Usage(1, 1));

    private final ProviderClient primary = mock(ProviderClient.class);
    private final ProviderClient secondary = mock(ProviderClient.class);

    private FailoverProviderClient chain() {
        return new FailoverProviderClient(List.of(primary, secondary));
    }

    private static CallNotPermittedException circuitOpen() {
        CircuitBreaker breaker = CircuitBreaker.ofDefaults("t");
        breaker.transitionToOpenState();
        return CallNotPermittedException.createCallNotPermittedException(breaker);
    }

    @Test
    void nameIsStable() {
        assertThat(chain().name()).isEqualTo("failover");
    }

    @Test
    void primarySuccessNeverTouchesSecondary() {
        when(primary.createMessage(any())).thenReturn(PRIMARY);

        assertThat(chain().createMessage(REQUEST)).isSameAs(PRIMARY);
        verifyNoInteractions(secondary);
    }

    @Test
    void openCircuitFailsOverToSecondary() {
        when(primary.createMessage(any())).thenThrow(circuitOpen());
        when(secondary.createMessage(any())).thenReturn(SECONDARY);

        assertThat(chain().createMessage(REQUEST)).isSameAs(SECONDARY);
    }

    @Test
    void serverErrorFailsOverToSecondary() {
        when(primary.createMessage(any())).thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));
        when(secondary.createMessage(any())).thenReturn(SECONDARY);

        assertThat(chain().createMessage(REQUEST)).isSameAs(SECONDARY);
    }

    @Test
    void transportErrorFailsOverToSecondary() {
        when(primary.createMessage(any())).thenThrow(new ResourceAccessException("connection reset"));
        when(secondary.createMessage(any())).thenReturn(SECONDARY);

        assertThat(chain().createMessage(REQUEST)).isSameAs(SECONDARY);
    }

    @Test
    void allProvidersDownThrowsUnavailable() {
        when(primary.createMessage(any())).thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));
        when(secondary.createMessage(any())).thenThrow(new ResourceAccessException("down"));

        assertThatThrownBy(() -> chain().createMessage(REQUEST))
                .isInstanceOf(AllProvidersUnavailableException.class);
    }

    @Test
    void clientErrorPropagatesWithoutFailover() {
        when(primary.createMessage(any())).thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> chain().createMessage(REQUEST))
                .isInstanceOf(HttpClientErrorException.class);
        verifyNoInteractions(secondary);
    }

    @Test
    void emptyChainIsRejected() {
        assertThatThrownBy(() -> new FailoverProviderClient(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failoverCallbackFiresOncePerFailedProvider() {
        when(primary.createMessage(any())).thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));
        when(secondary.createMessage(any())).thenReturn(SECONDARY);
        int[] failovers = {0};

        FailoverProviderClient chain =
                new FailoverProviderClient(List.of(primary, secondary), () -> failovers[0]++);
        assertThat(chain.createMessage(REQUEST)).isSameAs(SECONDARY);

        // The primary failed and we moved on: exactly one failover event.
        assertThat(failovers[0]).isEqualTo(1);
    }

    @Test
    void failoverCallbackFiresForEachDownProviderWhenAllFail() {
        when(primary.createMessage(any())).thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));
        when(secondary.createMessage(any())).thenThrow(new ResourceAccessException("down"));
        int[] failovers = {0};

        FailoverProviderClient chain =
                new FailoverProviderClient(List.of(primary, secondary), () -> failovers[0]++);
        assertThatThrownBy(() -> chain.createMessage(REQUEST))
                .isInstanceOf(AllProvidersUnavailableException.class);

        assertThat(failovers[0]).isEqualTo(2);
    }
}

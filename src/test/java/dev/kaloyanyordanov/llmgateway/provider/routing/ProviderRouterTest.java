package dev.kaloyanyordanov.llmgateway.provider.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.kaloyanyordanov.llmgateway.provider.AllProvidersUnavailableException;
import dev.kaloyanyordanov.llmgateway.provider.ProviderClient;
import dev.kaloyanyordanov.llmgateway.provider.ProviderRegistry;
import dev.kaloyanyordanov.llmgateway.proxy.Message;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesRequest;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesResponse;
import dev.kaloyanyordanov.llmgateway.proxy.Usage;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

class ProviderRouterTest {

    private static final MessagesRequest REQUEST =
            new MessagesRequest("flex", "sys", List.of(new Message("user", "hi")), null, null, 10, null);
    private static final MessagesResponse RESPONSE =
            new MessagesResponse("id", "message", "assistant", "m", "hi", "end_turn", new Usage(1, 1));

    private final ModelRouter modelRouter = mock(ModelRouter.class);
    private final ProviderRegistry registry = mock(ProviderRegistry.class);
    private final ProviderRouter router = new ProviderRouter(modelRouter, registry);

    @Test
    void noRuleUsesTheRegistryDefaultUnchanged() {
        ProviderClient defaultProvider = mock(ProviderClient.class);
        when(modelRouter.resolve("flex")).thenReturn(Optional.empty());
        when(registry.getDefault()).thenReturn(defaultProvider);
        when(defaultProvider.createMessage(REQUEST)).thenReturn(RESPONSE);

        assertThat(router.route(REQUEST)).isSameAs(RESPONSE);
    }

    @Test
    void routesToResolvedProviderWithRewrittenConcreteModel() {
        ProviderClient openai = mock(ProviderClient.class);
        when(modelRouter.resolve("flex"))
                .thenReturn(Optional.of(List.of(new RouteTarget("openai", "gpt-4o-mini"))));
        when(registry.get("openai")).thenReturn(openai);
        ArgumentCaptor<MessagesRequest> captor = ArgumentCaptor.forClass(MessagesRequest.class);
        when(openai.createMessage(captor.capture())).thenReturn(RESPONSE);

        assertThat(router.route(REQUEST)).isSameAs(RESPONSE);
        assertThat(captor.getValue().model()).isEqualTo("gpt-4o-mini");
        assertThat(captor.getValue().system()).isEqualTo("sys");
        assertThat(captor.getValue().messages()).isEqualTo(REQUEST.messages());
    }

    @Test
    void fallsOverToNextTargetOnAvailabilityFailure() {
        ProviderClient first = mock(ProviderClient.class);
        ProviderClient second = mock(ProviderClient.class);
        when(modelRouter.resolve("flex")).thenReturn(Optional.of(
                List.of(new RouteTarget("first", "m1"), new RouteTarget("second", "m2"))));
        when(registry.get("first")).thenReturn(first);
        when(registry.get("second")).thenReturn(second);
        when(first.createMessage(any())).thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));
        when(second.createMessage(any())).thenReturn(RESPONSE);

        assertThat(router.route(REQUEST)).isSameAs(RESPONSE);
    }

    @Test
    void throwsWhenAllTargetsAreUnavailable() {
        ProviderClient first = mock(ProviderClient.class);
        ProviderClient second = mock(ProviderClient.class);
        when(modelRouter.resolve("flex")).thenReturn(Optional.of(
                List.of(new RouteTarget("first", "m1"), new RouteTarget("second", "m2"))));
        when(registry.get("first")).thenReturn(first);
        when(registry.get("second")).thenReturn(second);
        when(first.createMessage(any())).thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));
        when(second.createMessage(any())).thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> router.route(REQUEST))
                .isInstanceOf(AllProvidersUnavailableException.class);
    }

    @Test
    void clientErrorPropagatesWithoutTryingNextTarget() {
        ProviderClient first = mock(ProviderClient.class);
        ProviderClient second = mock(ProviderClient.class);
        when(modelRouter.resolve("flex")).thenReturn(Optional.of(
                List.of(new RouteTarget("first", "m1"), new RouteTarget("second", "m2"))));
        when(registry.get("first")).thenReturn(first);
        when(first.createMessage(any())).thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> router.route(REQUEST)).isInstanceOf(HttpClientErrorException.class);
        verify(second, never()).createMessage(any());
    }

    @Test
    void validationRejectsUnknownReferencedProvider() {
        when(modelRouter.referencedProviders()).thenReturn(Set.of("ghost"));
        when(registry.names()).thenReturn(Set.of("anthropic"));

        assertThatThrownBy(router::validateReferencedProviders)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void validationPassesWhenEveryReferencedProviderIsKnown() {
        when(modelRouter.referencedProviders()).thenReturn(Set.of("anthropic"));
        when(registry.names()).thenReturn(Set.of("anthropic", "openai"));

        router.validateReferencedProviders();
    }
}

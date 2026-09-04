package dev.kaloyanyordanov.llmgateway.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.kaloyanyordanov.llmgateway.auth.ApiKeyAuthFilter;
import dev.kaloyanyordanov.llmgateway.auth.Client;
import dev.kaloyanyordanov.llmgateway.proxy.streaming.StreamingProxyService;
import dev.kaloyanyordanov.llmgateway.usage.BudgetExceededException;
import dev.kaloyanyordanov.llmgateway.usage.BudgetService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ProxyControllerTest {

    private final ProxyService proxyService = mock(ProxyService.class);
    private final StreamingProxyService streamingProxyService = mock(StreamingProxyService.class);
    private final BudgetService budgetService = mock(BudgetService.class);
    private final ProxyController controller =
            new ProxyController(proxyService, streamingProxyService, budgetService);

    private static MockHttpServletRequest requestFor(Client client) {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setAttribute(ApiKeyAuthFilter.CLIENT_ATTRIBUTE, client);
        return httpRequest;
    }

    @Test
    void nonStreamingRequestDelegatesToProxyService() {
        MessagesResponse expected =
                new MessagesResponse("id", "message", "assistant", "m", "hi", "end_turn", new Usage(1, 2));
        MessagesRequest request =
                new MessagesRequest("m", null, List.of(new Message("user", "hi")), null, null, 10, null);
        Client client = mock(Client.class);
        when(client.getId()).thenReturn(42L);
        when(proxyService.handle(eq(request), eq(42L))).thenReturn(expected);

        assertThat(controller.createMessage(request, requestFor(client))).isSameAs(expected);
    }

    @Test
    void streamingRequestDelegatesToStreamingService() {
        MessagesRequest request =
                new MessagesRequest("m", null, List.of(new Message("user", "hi")), null, null, 10, null, true);
        SseEmitter emitter = new SseEmitter();
        Client client = mock(Client.class);
        when(client.getId()).thenReturn(42L);
        when(streamingProxyService.stream(eq(request), eq(42L))).thenReturn(emitter);

        assertThat(controller.createMessage(request, requestFor(client))).isSameAs(emitter);
    }

    @Test
    void budgetIsEnforcedWithTheClientsCapBeforeAnyDispatch() {
        MessagesRequest request =
                new MessagesRequest("m", null, List.of(new Message("user", "hi")), null, null, 10, null);
        Client client = mock(Client.class);
        when(client.getId()).thenReturn(42L);
        when(client.getBudget()).thenReturn(new BigDecimal("1.00"));

        controller.createMessage(request, requestFor(client));

        verify(budgetService).enforce(42L, new BigDecimal("1.00"));
    }

    @Test
    void overBudgetClientIsRejectedWithoutCallingEitherService() {
        MessagesRequest request =
                new MessagesRequest("m", null, List.of(new Message("user", "hi")), null, null, 10, null, true);
        Client client = mock(Client.class);
        when(client.getId()).thenReturn(42L);
        when(client.getBudget()).thenReturn(new BigDecimal("1.00"));
        doThrow(new BudgetExceededException(new BigDecimal("1.00"), new BigDecimal("1.00")))
                .when(budgetService).enforce(42L, new BigDecimal("1.00"));

        assertThatThrownBy(() -> controller.createMessage(request, requestFor(client)))
                .isInstanceOf(BudgetExceededException.class);

        verifyNoInteractions(proxyService, streamingProxyService);
    }
}

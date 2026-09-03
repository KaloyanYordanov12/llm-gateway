package dev.kaloyanyordanov.llmgateway.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.kaloyanyordanov.llmgateway.auth.ApiKeyAuthFilter;
import dev.kaloyanyordanov.llmgateway.auth.Client;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ProxyControllerTest {

    @Test
    void extractsClientFromRequestAndDelegatesToProxyService() {
        MessagesResponse expected =
                new MessagesResponse("id", "message", "assistant", "m", "hi", "end_turn", new Usage(1, 2));
        MessagesRequest request =
                new MessagesRequest("m", null, List.of(new Message("user", "hi")), null, null, 10, null);

        Client client = mock(Client.class);
        when(client.getId()).thenReturn(42L);
        ProxyService proxyService = mock(ProxyService.class);
        when(proxyService.handle(eq(request), eq(42L))).thenReturn(expected);

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setAttribute(ApiKeyAuthFilter.CLIENT_ATTRIBUTE, client);

        ProxyController controller = new ProxyController(proxyService);

        assertThat(controller.createMessage(request, httpRequest)).isSameAs(expected);
    }
}

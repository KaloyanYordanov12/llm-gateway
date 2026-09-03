package dev.kaloyanyordanov.llmgateway.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProxyControllerTest {

    @Test
    void delegatesToProxyService() {
        MessagesResponse expected =
                new MessagesResponse("id", "message", "assistant", "m", "hi", "end_turn", new Usage(1, 2));
        ProxyService proxyService = mock(ProxyService.class);
        when(proxyService.handle(any())).thenReturn(expected);

        ProxyController controller = new ProxyController(proxyService);
        MessagesRequest request =
                new MessagesRequest("m", null, List.of(new Message("user", "hi")), null, null, 10, null);

        assertThat(controller.createMessage(request)).isSameAs(expected);
    }
}

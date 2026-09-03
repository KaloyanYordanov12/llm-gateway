package dev.kaloyanyordanov.llmgateway.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.kaloyanyordanov.llmgateway.provider.ProviderClient;
import dev.kaloyanyordanov.llmgateway.provider.ProviderRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProxyControllerTest {

    @Test
    void forwardsToDefaultProviderAndReturnsResponse() {
        MessagesResponse expected =
                new MessagesResponse("id", "message", "assistant", "m", "hi", "end_turn", new Usage(1, 2));
        ProviderClient provider = mock(ProviderClient.class);
        when(provider.createMessage(any())).thenReturn(expected);
        ProviderRegistry registry = mock(ProviderRegistry.class);
        when(registry.getDefault()).thenReturn(provider);

        ProxyController controller = new ProxyController(registry);
        MessagesRequest request =
                new MessagesRequest("m", null, List.of(new Message("user", "hi")), null, null, 10, null);

        assertThat(controller.createMessage(request)).isSameAs(expected);
    }
}

package dev.kaloyanyordanov.llmgateway.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.kaloyanyordanov.llmgateway.cache.ResponseCacheService;
import dev.kaloyanyordanov.llmgateway.provider.ProviderClient;
import dev.kaloyanyordanov.llmgateway.provider.ProviderRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProxyServiceTest {

    private static final MessagesResponse RESPONSE =
            new MessagesResponse("id", "message", "assistant", "m", "hi", "end_turn", new Usage(1, 1));
    private static final MessagesRequest REQUEST =
            new MessagesRequest("m", null, List.of(new Message("user", "hi")), null, null, 10, null);

    private final ProviderRegistry registry = mock(ProviderRegistry.class);
    private final ProviderClient provider = mock(ProviderClient.class);
    private final ResponseCacheService cache = mock(ResponseCacheService.class);
    private final ProxyService service = new ProxyService(registry, cache);

    @Test
    void cacheMissCallsProviderAndCachesResult() {
        when(cache.get(REQUEST)).thenReturn(Optional.empty());
        when(registry.getDefault()).thenReturn(provider);
        when(provider.createMessage(REQUEST)).thenReturn(RESPONSE);

        assertThat(service.handle(REQUEST)).isSameAs(RESPONSE);
        verify(provider).createMessage(REQUEST);
        verify(cache).put(REQUEST, RESPONSE);
    }

    @Test
    void cacheHitReturnsCachedAndSkipsProvider() {
        when(cache.get(REQUEST)).thenReturn(Optional.of(RESPONSE));

        assertThat(service.handle(REQUEST)).isSameAs(RESPONSE);
        verify(registry, never()).getDefault();
        verify(cache, never()).put(any(), any());
    }
}

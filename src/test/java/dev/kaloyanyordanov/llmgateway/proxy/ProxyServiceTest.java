package dev.kaloyanyordanov.llmgateway.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.kaloyanyordanov.llmgateway.cache.ResponseCacheService;
import dev.kaloyanyordanov.llmgateway.provider.ProviderClient;
import dev.kaloyanyordanov.llmgateway.provider.ProviderRegistry;
import dev.kaloyanyordanov.llmgateway.usage.PricingService;
import dev.kaloyanyordanov.llmgateway.usage.UnknownModelException;
import dev.kaloyanyordanov.llmgateway.usage.UsageService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProxyServiceTest {

    private static final long CLIENT_ID = 7L;
    private static final MessagesResponse RESPONSE =
            new MessagesResponse("id", "message", "assistant", "m", "hi", "end_turn", new Usage(12, 5));
    private static final MessagesRequest REQUEST =
            new MessagesRequest("m", null, List.of(new Message("user", "hi")), null, null, 10, null);

    private final ProviderRegistry registry = mock(ProviderRegistry.class);
    private final ProviderClient provider = mock(ProviderClient.class);
    private final ResponseCacheService cache = mock(ResponseCacheService.class);
    private final PricingService pricingService = mock(PricingService.class);
    private final UsageService usageService = mock(UsageService.class);
    private final ProxyService service =
            new ProxyService(registry, cache, pricingService, usageService);

    @Test
    void unknownModelIsRejectedBeforeAnyProviderOrCacheAccess() {
        when(pricingService.isKnown("m")).thenReturn(false);

        assertThatThrownBy(() -> service.handle(REQUEST, CLIENT_ID))
                .isInstanceOf(UnknownModelException.class);

        verifyNoInteractions(cache, registry, usageService);
    }

    @Test
    void cacheHitReturnsCachedWithoutProviderOrUsage() {
        when(pricingService.isKnown("m")).thenReturn(true);
        when(cache.get(REQUEST)).thenReturn(Optional.of(RESPONSE));

        assertThat(service.handle(REQUEST, CLIENT_ID)).isSameAs(RESPONSE);

        verify(registry, never()).getDefault();
        verifyNoInteractions(usageService);
        verify(cache, never()).put(any(), any());
    }

    @Test
    void cacheMissCallsProviderRecordsUsageAndCaches() {
        when(pricingService.isKnown("m")).thenReturn(true);
        when(cache.get(REQUEST)).thenReturn(Optional.empty());
        when(registry.getDefault()).thenReturn(provider);
        when(provider.createMessage(REQUEST)).thenReturn(RESPONSE);
        when(pricingService.cost("m", 12, 5)).thenReturn(new BigDecimal("0.10"));

        assertThat(service.handle(REQUEST, CLIENT_ID)).isSameAs(RESPONSE);

        verify(usageService).record(CLIENT_ID, "m", 12, 5, new BigDecimal("0.10"));
        verify(cache).put(REQUEST, RESPONSE);
    }
}

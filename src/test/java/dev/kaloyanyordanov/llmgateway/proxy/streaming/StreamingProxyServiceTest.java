package dev.kaloyanyordanov.llmgateway.proxy.streaming;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.kaloyanyordanov.llmgateway.cache.ResponseCacheService;
import dev.kaloyanyordanov.llmgateway.proxy.Message;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesRequest;
import dev.kaloyanyordanov.llmgateway.usage.PricingService;
import dev.kaloyanyordanov.llmgateway.usage.UnknownModelException;
import dev.kaloyanyordanov.llmgateway.usage.UsageService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.web.client.HttpServerErrorException;

class StreamingProxyServiceTest {

    private static final long CLIENT_ID = 7L;
    private static final MessagesRequest REQUEST =
            new MessagesRequest("m", null, List.of(new Message("user", "hi")), null, null, 10, null, true);

    private final StreamingProviderClient streamingClient = mock(StreamingProviderClient.class);
    private final ResponseCacheService cache = mock(ResponseCacheService.class);
    private final PricingService pricingService = mock(PricingService.class);
    private final UsageService usageService = mock(UsageService.class);
    // Direct executor: run the streaming task inline so effects are observable.
    private final StreamingProxyService service = new StreamingProxyService(
            streamingClient, cache, pricingService, usageService, Runnable::run);

    private Answer<Void> populate(boolean complete) {
        return invocation -> {
            StreamAccumulator accumulator = invocation.getArgument(1);
            accumulator.start("id", "m", 10);
            accumulator.appendText("hello");
            accumulator.outputTokens(5);
            if (complete) {
                accumulator.markComplete();
            }
            return null;
        };
    }

    @Test
    void unknownModelIsRejectedBeforeStreaming() {
        when(pricingService.isKnown("m")).thenReturn(false);

        assertThatThrownBy(() -> service.stream(REQUEST, CLIENT_ID))
                .isInstanceOf(UnknownModelException.class);

        verifyNoInteractions(streamingClient, cache, usageService);
    }

    @Test
    void cacheHitReplaysWithoutStreamingOrUsage() {
        when(pricingService.isKnown("m")).thenReturn(true);
        when(cache.get(REQUEST)).thenReturn(Optional.of(
                new dev.kaloyanyordanov.llmgateway.proxy.MessagesResponse(
                        "id", "message", "assistant", "m", "cached", "end_turn", null)));

        service.stream(REQUEST, CLIENT_ID);

        verifyNoInteractions(streamingClient, usageService);
    }

    @Test
    void completeStreamRecordsUsageAndCaches() {
        when(pricingService.isKnown("m")).thenReturn(true);
        when(cache.get(REQUEST)).thenReturn(Optional.empty());
        when(pricingService.cost("m", 10, 5)).thenReturn(new BigDecimal("0.10"));
        doAnswer(populate(true)).when(streamingClient).stream(any(), any(), any());

        service.stream(REQUEST, CLIENT_ID);

        verify(usageService).record(CLIENT_ID, "m", 10, 5, new BigDecimal("0.10"), true);
        verify(cache).put(eq(REQUEST), any());
    }

    @Test
    void truncatedStreamRecordsPartialAndCachesNothing() {
        when(pricingService.isKnown("m")).thenReturn(true);
        when(cache.get(REQUEST)).thenReturn(Optional.empty());
        when(pricingService.cost("m", 10, 5)).thenReturn(new BigDecimal("0.10"));
        doAnswer(populate(false)).when(streamingClient).stream(any(), any(), any());

        service.stream(REQUEST, CLIENT_ID);

        verify(usageService).record(CLIENT_ID, "m", 10, 5, new BigDecimal("0.10"), false);
        verify(cache, never()).put(any(), any());
    }

    @Test
    void failureAfterPartialRecordsPartial() {
        when(pricingService.isKnown("m")).thenReturn(true);
        when(cache.get(REQUEST)).thenReturn(Optional.empty());
        when(pricingService.cost("m", 10, 5)).thenReturn(new BigDecimal("0.10"));
        doAnswer(invocation -> {
            StreamAccumulator accumulator = invocation.getArgument(1);
            accumulator.start("id", "m", 10);
            accumulator.appendText("hello");
            accumulator.outputTokens(5);
            throw new HttpServerErrorException(org.springframework.http.HttpStatus.BAD_GATEWAY);
        }).when(streamingClient).stream(any(), any(), any());

        service.stream(REQUEST, CLIENT_ID);

        verify(usageService).record(CLIENT_ID, "m", 10, 5, new BigDecimal("0.10"), false);
        verify(cache, never()).put(any(), any());
    }

    @Test
    void failureBeforeFirstByteRecordsNoUsage() {
        when(pricingService.isKnown("m")).thenReturn(true);
        when(cache.get(REQUEST)).thenReturn(Optional.empty());
        doThrow(new HttpServerErrorException(org.springframework.http.HttpStatus.BAD_GATEWAY))
                .when(streamingClient).stream(any(), any(), any());

        service.stream(REQUEST, CLIENT_ID);

        verify(usageService, never()).record(any(), any(), anyInt(), anyInt(), any(), anyBoolean());
        verify(cache, never()).put(any(), any());
    }
}

package dev.kaloyanyordanov.llmgateway.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.llmgateway.proxy.Message;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesRequest;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesResponse;
import dev.kaloyanyordanov.llmgateway.proxy.Usage;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResponseCacheServiceTest {

    private static final MessagesResponse RESPONSE =
            new MessagesResponse("id", "message", "assistant", "m", "hi", "end_turn", new Usage(1, 1));

    private final FakeTicker ticker = new FakeTicker();
    private final ResponseCacheService cache = new ResponseCacheService(
            new CacheKeyGenerator(), new CacheProperties(Duration.ofHours(1), 1000), ticker);

    private static MessagesRequest request(String content, Double temperature) {
        return new MessagesRequest(
                "m", null, List.of(new Message("user", content)), temperature, null, 10, null);
    }

    @Test
    void missThenPutThenHitWithCounters() {
        MessagesRequest request = request("hi", null);

        assertThat(cache.get(request)).isEmpty();
        cache.put(request, RESPONSE);
        assertThat(cache.get(request)).contains(RESPONSE);

        assertThat(cache.hitCount()).isEqualTo(1);
        assertThat(cache.missCount()).isEqualTo(1);
    }

    @Test
    void differentRequestMisses() {
        cache.put(request("hi", null), RESPONSE);

        assertThat(cache.get(request("hi there", null))).isEmpty();
    }

    @Test
    void entryExpiresAfterTtl() {
        MessagesRequest request = request("hi", null);
        cache.put(request, RESPONSE);
        assertThat(cache.get(request)).isPresent();

        ticker.advance(Duration.ofHours(1).plusSeconds(1));

        assertThat(cache.get(request)).isEmpty();
    }

    @Test
    void nonZeroTemperatureHitReturnsPriorCompletion() {
        MessagesRequest hot = request("hi", 0.9);
        cache.put(hot, RESPONSE);

        // Documented behaviour: a hit returns the prior completion, no resampling.
        assertThat(cache.get(hot)).contains(RESPONSE);
    }
}

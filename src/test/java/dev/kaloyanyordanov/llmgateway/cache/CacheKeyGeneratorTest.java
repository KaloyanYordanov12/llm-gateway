package dev.kaloyanyordanov.llmgateway.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.llmgateway.proxy.Message;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class CacheKeyGeneratorTest {

    private final CacheKeyGenerator generator = new CacheKeyGenerator();

    private static MessagesRequest request(String content, Double temperature) {
        return new MessagesRequest(
                "m", null, List.of(new Message("user", content)), temperature, null, 10, null);
    }

    @Test
    void identicalRequestsProduceTheSameKey() {
        assertThat(generator.key(request("hi", 0.5))).isEqualTo(generator.key(request("hi", 0.5)));
    }

    @Test
    void aSingleTokenDifferenceProducesADifferentKey() {
        assertThat(generator.key(request("hi", 0.5)))
                .isNotEqualTo(generator.key(request("hi there", 0.5)));
    }

    @Test
    void differentTopPProducesADifferentKey() {
        MessagesRequest a = new MessagesRequest("m", null, List.of(new Message("user", "hi")), null, 0.9, 10, null);
        MessagesRequest b = new MessagesRequest("m", null, List.of(new Message("user", "hi")), null, 0.8, 10, null);

        assertThat(generator.key(a)).isNotEqualTo(generator.key(b));
    }

    @Test
    void keyIsHexEncodedSha256() {
        assertThat(generator.key(request("hi", 0.5))).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void streamFlagDoesNotAffectTheKey() {
        MessagesRequest nonStreaming =
                new MessagesRequest("m", null, List.of(new Message("user", "hi")), null, null, 10, null, false);
        MessagesRequest streaming =
                new MessagesRequest("m", null, List.of(new Message("user", "hi")), null, null, 10, null, true);
        MessagesRequest absent =
                new MessagesRequest("m", null, List.of(new Message("user", "hi")), null, null, 10, null);

        assertThat(generator.key(streaming)).isEqualTo(generator.key(nonStreaming));
        assertThat(generator.key(streaming)).isEqualTo(generator.key(absent));
    }
}

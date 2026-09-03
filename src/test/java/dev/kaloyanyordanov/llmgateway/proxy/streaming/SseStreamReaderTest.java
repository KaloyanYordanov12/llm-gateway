package dev.kaloyanyordanov.llmgateway.proxy.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SseStreamReaderTest {

    private final SseStreamReader reader = new SseStreamReader();

    private static final String START =
            "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"model\":\"claude-x\","
            + "\"usage\":{\"input_tokens\":10,\"output_tokens\":0}}}";
    private static final String DELTA_HELLO =
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}";
    private static final String DELTA_WORLD =
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\" world\"}}";
    private static final String MESSAGE_DELTA =
            "data: {\"type\":\"message_delta\",\"usage\":{\"output_tokens\":5}}";
    private static final String STOP = "data: {\"type\":\"message_stop\"}";

    private StreamResult read(List<String> deltas, String... lines) {
        StreamAccumulator accumulator = new StreamAccumulator();
        reader.read(List.of(lines).stream(), accumulator, deltas::add);
        return accumulator.toResult();
    }

    @Test
    void assemblesCleanStreamWithTokensAndForwardsDeltas() {
        List<String> deltas = new ArrayList<>();
        StreamResult result = read(deltas, START, DELTA_HELLO, DELTA_WORLD, MESSAGE_DELTA, STOP);

        assertThat(result.id()).isEqualTo("msg_1");
        assertThat(result.model()).isEqualTo("claude-x");
        assertThat(result.content()).isEqualTo("Hello world");
        assertThat(result.inputTokens()).isEqualTo(10);
        assertThat(result.outputTokens()).isEqualTo(5);
        assertThat(result.complete()).isTrue();
        assertThat(deltas).containsExactly("Hello", " world");
    }

    @Test
    void truncatedStreamIsIncompleteButKeepsWhatArrived() {
        List<String> deltas = new ArrayList<>();
        // No message_delta, no message_stop — the stream aborted after one delta.
        StreamResult result = read(deltas, START, DELTA_HELLO);

        assertThat(result.content()).isEqualTo("Hello");
        assertThat(result.inputTokens()).isEqualTo(10);
        assertThat(result.outputTokens()).isZero();
        assertThat(result.complete()).isFalse();
        assertThat(deltas).containsExactly("Hello");
    }

    @Test
    void ignoresNonDataCommentAndUnknownLines() {
        List<String> deltas = new ArrayList<>();
        StreamResult result = read(deltas,
                "event: message_start",
                ": this is a comment",
                "",
                START,
                "data: {\"type\":\"ping\"}",
                DELTA_HELLO,
                STOP);

        assertThat(result.content()).isEqualTo("Hello");
        assertThat(result.complete()).isTrue();
        assertThat(deltas).containsExactly("Hello");
    }
}

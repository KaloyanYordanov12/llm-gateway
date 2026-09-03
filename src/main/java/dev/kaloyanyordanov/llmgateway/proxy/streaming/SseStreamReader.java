package dev.kaloyanyordanov.llmgateway.proxy.streaming;

import java.util.Iterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes an upstream provider's SSE stream line-by-line (Anthropic-style events),
 * accumulating the assembled text and token counts while forwarding each text delta
 * to a consumer for downstream re-emission. Pure with respect to transport: it takes
 * a stream of lines, so it is fully unit-testable without any network.
 */
@Component
public class SseStreamReader {

    private static final String DATA_PREFIX = "data:";

    private final JsonMapper mapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    /**
     * Reads the stream, forwarding each text delta and updating the accumulator as
     * events arrive. If reading throws part-way, the accumulator still holds what
     * arrived so a partial result can be recovered.
     *
     * @param lines       the upstream SSE lines
     * @param accumulator the accumulator to update
     * @param onTextDelta called with each text delta as it arrives (for downstream)
     */
    public void read(Stream<String> lines, StreamAccumulator accumulator, Consumer<String> onTextDelta) {
        Iterator<String> iterator = lines.iterator();
        while (iterator.hasNext()) {
            SseEvent event = parse(iterator.next());
            if (event == null || event.type() == null) {
                continue;
            }
            switch (event.type()) {
                case "message_start" -> {
                    SseMessage message = event.message();
                    if (message != null) {
                        accumulator.start(message.id(), message.model(),
                                tokensOr(message.usage() == null ? null : message.usage().inputTokens(), 0));
                    }
                }
                case "content_block_delta" -> {
                    if (event.delta() != null && event.delta().text() != null) {
                        accumulator.appendText(event.delta().text());
                        onTextDelta.accept(event.delta().text());
                    }
                }
                case "message_delta" -> {
                    if (event.usage() != null && event.usage().outputTokens() != null) {
                        accumulator.outputTokens(event.usage().outputTokens());
                    }
                }
                case "message_stop" -> accumulator.markComplete();
                default -> {
                    // ignore unknown event types
                }
            }
        }
    }

    private SseEvent parse(String line) {
        if (!line.startsWith(DATA_PREFIX)) {
            return null;
        }
        String json = line.substring(DATA_PREFIX.length()).trim();
        if (json.isEmpty()) {
            return null;
        }
        return mapper.readValue(json, SseEvent.class);
    }

    private static int tokensOr(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    record SseEvent(String type, SseMessage message, SseDelta delta, SseUsage usage) {
    }

    record SseMessage(String id, String model, SseUsage usage) {
    }

    record SseDelta(String type, String text) {
    }

    record SseUsage(Integer inputTokens, Integer outputTokens) {
    }
}

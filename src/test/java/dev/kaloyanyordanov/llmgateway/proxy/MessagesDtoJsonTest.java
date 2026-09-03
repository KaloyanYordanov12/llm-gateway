package dev.kaloyanyordanov.llmgateway.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * Validates that the messages DTOs map to Anthropic's snake_case wire shape
 * ({@code top_p}, {@code max_tokens}, {@code stop_sequences}, {@code input_tokens},
 * {@code output_tokens}) under the same naming strategy the app configures.
 */
class MessagesDtoJsonTest {

    private final JsonMapper mapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    @Test
    void requestDeserializesAnthropicSnakeCaseFields() {
        String json = """
                {
                  "model": "claude-x",
                  "system": "be brief",
                  "messages": [{"role": "user", "content": "hi"}],
                  "temperature": 0.7,
                  "top_p": 0.9,
                  "max_tokens": 100,
                  "stop_sequences": ["STOP"]
                }""";

        MessagesRequest req = mapper.readValue(json, MessagesRequest.class);

        assertThat(req.model()).isEqualTo("claude-x");
        assertThat(req.system()).isEqualTo("be brief");
        assertThat(req.messages()).hasSize(1);
        assertThat(req.messages().get(0).role()).isEqualTo("user");
        assertThat(req.messages().get(0).content()).isEqualTo("hi");
        assertThat(req.temperature()).isEqualTo(0.7);
        assertThat(req.topP()).isEqualTo(0.9);
        assertThat(req.maxTokens()).isEqualTo(100);
        assertThat(req.stopSequences()).containsExactly("STOP");
    }

    @Test
    void requestSerializesBackToSnakeCase() {
        MessagesRequest req = new MessagesRequest(
                "m", null, List.of(new Message("user", "hi")), null, 0.9, 100, List.of("STOP"));

        String json = mapper.writeValueAsString(req);

        assertThat(json)
                .contains("\"top_p\":0.9")
                .contains("\"max_tokens\":100")
                .contains("\"stop_sequences\":[\"STOP\"]");
    }

    @Test
    void responseAndUsageMapSnakeCase() {
        String json = """
                {
                  "id": "msg_1",
                  "type": "message",
                  "role": "assistant",
                  "model": "m",
                  "content": "hello",
                  "stop_reason": "end_turn",
                  "usage": {"input_tokens": 12, "output_tokens": 5}
                }""";

        MessagesResponse resp = mapper.readValue(json, MessagesResponse.class);

        assertThat(resp.id()).isEqualTo("msg_1");
        assertThat(resp.type()).isEqualTo("message");
        assertThat(resp.role()).isEqualTo("assistant");
        assertThat(resp.model()).isEqualTo("m");
        assertThat(resp.content()).isEqualTo("hello");
        assertThat(resp.stopReason()).isEqualTo("end_turn");
        assertThat(resp.usage().inputTokens()).isEqualTo(12);
        assertThat(resp.usage().outputTokens()).isEqualTo(5);
    }
}

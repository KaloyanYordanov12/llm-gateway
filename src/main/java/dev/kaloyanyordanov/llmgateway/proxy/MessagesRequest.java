package dev.kaloyanyordanov.llmgateway.proxy;

import java.util.List;

/**
 * Inbound request mirroring Anthropic's {@code /v1/messages} body. The field set
 * is exactly the one locked for the cache key: {@code model}, {@code system},
 * {@code messages}, {@code temperature}, {@code top_p}, {@code max_tokens},
 * {@code stop_sequences}. Optional fields may be {@code null}.
 *
 * @param model         the provider model id (validated against the allowlist later)
 * @param system        optional system prompt
 * @param messages      the ordered conversation messages
 * @param temperature   optional sampling temperature
 * @param topP          optional nucleus-sampling probability ({@code top_p})
 * @param maxTokens     maximum tokens to generate ({@code max_tokens})
 * @param stopSequences optional stop sequences ({@code stop_sequences})
 */
public record MessagesRequest(
        String model,
        String system,
        List<Message> messages,
        Double temperature,
        Double topP,
        Integer maxTokens,
        List<String> stopSequences) {

    /**
     * Defensive-copy compact constructor: stores immutable copies of the list
     * fields (null preserved) so the record is a true immutable value object.
     */
    public MessagesRequest {
        messages = messages == null ? null : List.copyOf(messages);
        stopSequences = stopSequences == null ? null : List.copyOf(stopSequences);
    }
}

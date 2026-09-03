package dev.kaloyanyordanov.llmgateway.proxy;

import java.util.List;

/**
 * Inbound request mirroring Anthropic's {@code /v1/messages} body. The first seven
 * fields are exactly the locked cache-key set: {@code model}, {@code system},
 * {@code messages}, {@code temperature}, {@code top_p}, {@code max_tokens},
 * {@code stop_sequences}. The optional {@code stream} flag opts into streaming and
 * is deliberately excluded from the cache key (see {@code CacheKeyGenerator}), so a
 * streamed and a non-streamed request for the same messages share a cache entry.
 *
 * @param model         the provider model id (validated against the allowlist later)
 * @param system        optional system prompt
 * @param messages      the ordered conversation messages
 * @param temperature   optional sampling temperature
 * @param topP          optional nucleus-sampling probability ({@code top_p})
 * @param maxTokens     maximum tokens to generate ({@code max_tokens})
 * @param stopSequences optional stop sequences ({@code stop_sequences})
 * @param stream        opt-in streaming flag (not part of the cache key)
 */
public record MessagesRequest(
        String model,
        String system,
        List<Message> messages,
        Double temperature,
        Double topP,
        Integer maxTokens,
        List<String> stopSequences,
        Boolean stream) {

    /**
     * Defensive-copy compact constructor: stores immutable copies of the list
     * fields (null preserved) so the record is a true immutable value object.
     */
    public MessagesRequest {
        messages = messages == null ? null : List.copyOf(messages);
        stopSequences = stopSequences == null ? null : List.copyOf(stopSequences);
    }

    /**
     * Backward-compatible non-streaming constructor: existing call sites keep the
     * seven-field shape and {@code stream} defaults to {@code null} (not streaming).
     *
     * @param model         the provider model id
     * @param system        optional system prompt
     * @param messages      the ordered conversation messages
     * @param temperature   optional sampling temperature
     * @param topP          optional nucleus-sampling probability
     * @param maxTokens     maximum tokens to generate
     * @param stopSequences optional stop sequences
     */
    public MessagesRequest(String model, String system, List<Message> messages, Double temperature,
            Double topP, Integer maxTokens, List<String> stopSequences) {
        this(model, system, messages, temperature, topP, maxTokens, stopSequences, null);
    }

    /**
     * @return whether the client requested a streamed response
     */
    public boolean isStreaming() {
        return Boolean.TRUE.equals(stream);
    }
}

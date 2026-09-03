package dev.kaloyanyordanov.llmgateway.provider.openai;

import java.util.List;

/**
 * Request body for OpenAI's {@code /v1/chat/completions}. Field names serialize to
 * OpenAI's snake_case ({@code max_tokens}, {@code top_p}) under the app's naming
 * strategy.
 *
 * @param model       the OpenAI model id
 * @param messages    the conversation messages (system prompt folded in as the first)
 * @param maxTokens   maximum tokens to generate ({@code max_tokens})
 * @param temperature optional sampling temperature
 * @param topP        optional nucleus-sampling probability ({@code top_p})
 * @param stop        optional stop sequences ({@code stop})
 */
public record OpenAiChatRequest(
        String model,
        List<OpenAiChatMessage> messages,
        Integer maxTokens,
        Double temperature,
        Double topP,
        List<String> stop) {

    /** Defensive-copy compact constructor (null lists preserved). */
    public OpenAiChatRequest {
        messages = messages == null ? null : List.copyOf(messages);
        stop = stop == null ? null : List.copyOf(stop);
    }
}

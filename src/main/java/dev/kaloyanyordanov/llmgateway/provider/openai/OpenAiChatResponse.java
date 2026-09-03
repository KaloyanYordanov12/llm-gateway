package dev.kaloyanyordanov.llmgateway.provider.openai;

import java.util.List;

/**
 * Response body from OpenAI's {@code /v1/chat/completions}.
 *
 * @param id      provider-assigned response id
 * @param model   the model that produced the response
 * @param choices the completion choices (first is used)
 * @param usage   token usage
 */
public record OpenAiChatResponse(
        String id,
        String model,
        List<OpenAiChoice> choices,
        OpenAiUsage usage) {

    /** Defensive-copy compact constructor (null list preserved). */
    public OpenAiChatResponse {
        choices = choices == null ? null : List.copyOf(choices);
    }
}

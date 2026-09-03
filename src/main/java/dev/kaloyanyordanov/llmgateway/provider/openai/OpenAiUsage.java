package dev.kaloyanyordanov.llmgateway.provider.openai;

/**
 * OpenAI's {@code usage} object.
 *
 * @param promptTokens     prompt tokens ({@code prompt_tokens})
 * @param completionTokens completion tokens ({@code completion_tokens})
 * @param totalTokens      total tokens ({@code total_tokens})
 */
public record OpenAiUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
}

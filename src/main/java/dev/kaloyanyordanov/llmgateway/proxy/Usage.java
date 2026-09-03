package dev.kaloyanyordanov.llmgateway.proxy;

/**
 * Token usage reported by the provider, mirroring Anthropic's {@code usage}
 * object. These are the counts the usage/cost accounting reads.
 *
 * @param inputTokens  prompt tokens consumed ({@code input_tokens})
 * @param outputTokens completion tokens produced ({@code output_tokens})
 */
public record Usage(Integer inputTokens, Integer outputTokens) {
}

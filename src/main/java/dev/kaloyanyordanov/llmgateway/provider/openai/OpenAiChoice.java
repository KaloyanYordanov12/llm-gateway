package dev.kaloyanyordanov.llmgateway.provider.openai;

/**
 * A single completion choice in OpenAI's chat-completions response.
 *
 * @param index        the choice index
 * @param message      the assistant message
 * @param finishReason why generation stopped ({@code finish_reason})
 */
public record OpenAiChoice(Integer index, OpenAiChatMessage message, String finishReason) {
}

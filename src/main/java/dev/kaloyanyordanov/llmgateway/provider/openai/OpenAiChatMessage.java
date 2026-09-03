package dev.kaloyanyordanov.llmgateway.provider.openai;

/**
 * A single message in OpenAI's chat-completions shape.
 *
 * @param role    the message role ({@code system} | {@code user} | {@code assistant})
 * @param content the message text content
 */
public record OpenAiChatMessage(String role, String content) {
}

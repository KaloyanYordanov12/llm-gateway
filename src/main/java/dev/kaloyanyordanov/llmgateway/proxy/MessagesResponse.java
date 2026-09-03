package dev.kaloyanyordanov.llmgateway.proxy;

/**
 * Outbound response mirroring Anthropic's {@code /v1/messages} response. v1
 * carries the fields the gateway needs for pass-through and accounting; the
 * generated {@code content} text is preserved for cache replay.
 *
 * @param id         provider-assigned response id
 * @param type       response type discriminator (e.g. {@code message})
 * @param role       the assistant role
 * @param model      the model that produced the response
 * @param content    the generated text content
 * @param stopReason why generation stopped ({@code stop_reason})
 * @param usage      token usage for accounting
 */
public record MessagesResponse(
        String id,
        String type,
        String role,
        String model,
        String content,
        String stopReason,
        Usage usage) {
}

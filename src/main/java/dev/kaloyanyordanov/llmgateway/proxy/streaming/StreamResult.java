package dev.kaloyanyordanov.llmgateway.proxy.streaming;

/**
 * The outcome of consuming an upstream stream: the assembled response and the
 * accumulated token counts. {@code complete} is true only if the stream ended
 * cleanly (a {@code message_stop} event was seen); a stream that aborted mid-way
 * yields {@code complete == false} and whatever was accumulated so far.
 *
 * @param id           provider-assigned response id (may be null)
 * @param model        the model that produced the response (may be null)
 * @param content      the assembled text content
 * @param inputTokens  accumulated prompt tokens
 * @param outputTokens accumulated completion tokens
 * @param complete     whether the stream ended cleanly
 */
public record StreamResult(
        String id,
        String model,
        String content,
        int inputTokens,
        int outputTokens,
        boolean complete) {
}

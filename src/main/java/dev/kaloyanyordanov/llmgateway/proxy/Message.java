package dev.kaloyanyordanov.llmgateway.proxy;

/**
 * A single conversation message in the Anthropic {@code messages} shape.
 *
 * <p>v1 models {@code content} as plain text (the common case). Structured
 * content blocks are a v2 concern; the gateway passes requests through, so the
 * typed shape only needs the fields that participate in the cache key.</p>
 *
 * @param role    the message role, e.g. {@code user} or {@code assistant}
 * @param content the message text content
 */
public record Message(String role, String content) {
}

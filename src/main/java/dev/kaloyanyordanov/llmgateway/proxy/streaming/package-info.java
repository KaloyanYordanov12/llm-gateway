/**
 * Streaming (SSE) support: reading an upstream provider's server-sent-events
 * stream line-by-line (blocking, on a virtual thread — no reactive model),
 * accumulating the assembled response and token counts, and re-emitting downstream
 * via a Spring {@code SseEmitter}.
 */
package dev.kaloyanyordanov.llmgateway.proxy.streaming;

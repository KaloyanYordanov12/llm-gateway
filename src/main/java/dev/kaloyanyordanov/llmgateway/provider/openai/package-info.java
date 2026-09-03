/**
 * OpenAI provider adapter: the chat-completions wire DTOs and the
 * {@code OpenAiProviderClient} that maps the gateway's normalized messages shape
 * to and from them. Isolated here because, unlike Anthropic, OpenAI's shape is not
 * the gateway's native shape and needs translation.
 */
package dev.kaloyanyordanov.llmgateway.provider.openai;

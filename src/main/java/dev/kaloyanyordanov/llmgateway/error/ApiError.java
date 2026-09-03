package dev.kaloyanyordanov.llmgateway.error;

/**
 * The API error envelope, mirroring Anthropic's shape for drop-in client
 * compatibility:
 * <pre>
 * { "type": "error", "error": { "type": ..., "message": ... }, "request_id": ... }
 * </pre>
 * The {@code type} is always the literal {@code "error"}; {@code requestId}
 * serializes as {@code request_id} (snake_case) and ties a response to logs and
 * the dashboard.
 *
 * @param type      the constant envelope discriminator, always {@code "error"}
 * @param error     the nested error detail (inner type + message)
 * @param requestId correlation id, serialized as {@code request_id}
 */
public record ApiError(String type, ErrorDetail error, String requestId) {
}

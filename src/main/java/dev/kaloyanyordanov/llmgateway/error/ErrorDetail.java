package dev.kaloyanyordanov.llmgateway.error;

/**
 * The inner {@code error} object of the API error envelope: a machine-readable
 * {@code type} and a human-readable {@code message}.
 *
 * @param type    machine-readable error type (e.g. {@code authentication_error})
 * @param message human-readable description of what went wrong
 */
public record ErrorDetail(String type, String message) {
}

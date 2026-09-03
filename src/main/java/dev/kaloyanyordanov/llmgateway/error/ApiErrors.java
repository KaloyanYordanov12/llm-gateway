package dev.kaloyanyordanov.llmgateway.error;

import java.util.UUID;

/**
 * Factory for {@link ApiError} envelopes. Centralizes the constant envelope
 * {@code type} ("error") and request-id generation so every error path emits an
 * identically-shaped envelope.
 */
public final class ApiErrors {

    /** The constant outer envelope type mandated by the error contract. */
    public static final String ENVELOPE_TYPE = "error";

    private ApiErrors() {
    }

    /**
     * Builds an error envelope with a freshly generated {@code request_id}.
     *
     * @param errorType machine-readable error type (e.g. {@code authentication_error})
     * @param message   human-readable description
     * @return a populated {@link ApiError}
     */
    public static ApiError of(String errorType, String message) {
        return of(errorType, message, UUID.randomUUID().toString());
    }

    /**
     * Builds an error envelope with a caller-supplied {@code request_id} (used
     * when the id already exists on the request for cross-log correlation).
     *
     * @param errorType machine-readable error type
     * @param message   human-readable description
     * @param requestId the request id to embed
     * @return a populated {@link ApiError}
     */
    public static ApiError of(String errorType, String message, String requestId) {
        return new ApiError(ENVELOPE_TYPE, new ErrorDetail(errorType, message), requestId);
    }
}

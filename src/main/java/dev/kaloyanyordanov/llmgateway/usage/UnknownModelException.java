package dev.kaloyanyordanov.llmgateway.usage;

/**
 * Thrown when a request names a model that is not in the configured pricing
 * allowlist. Fail-secure: the request is rejected rather than proxied unpriced.
 */
public class UnknownModelException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param model the unknown model id
     */
    public UnknownModelException(String model) {
        super("Unknown model: " + model);
    }
}

package dev.kaloyanyordanov.llmgateway.provider;

/**
 * Thrown when every provider in the failover chain is unavailable (circuit open,
 * or failing after retries). Maps to a clean {@code 503} rather than a hang.
 */
public class AllProvidersUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param cause the last provider failure that exhausted the chain
     */
    public AllProvidersUnavailableException(Throwable cause) {
        super("All providers in the failover chain are unavailable", cause);
    }
}

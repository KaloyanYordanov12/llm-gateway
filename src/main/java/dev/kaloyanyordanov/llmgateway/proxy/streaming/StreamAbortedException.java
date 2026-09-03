package dev.kaloyanyordanov.llmgateway.proxy.streaming;

/**
 * Signals that downstream delivery to the client failed (e.g. the client
 * disconnected), so upstream reading should stop and resources be released. Breaks
 * the read loop and is handled as a mid-stream abort.
 */
public class StreamAbortedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param cause the downstream I/O failure
     */
    public StreamAbortedException(Throwable cause) {
        super("Downstream stream aborted", cause);
    }
}

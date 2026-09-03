package dev.kaloyanyordanov.llmgateway.proxy.streaming;

/**
 * Mutable accumulation of a stream as it is read. Kept outside the reader so that
 * if the stream aborts mid-way (an exception during reading), the orchestrator can
 * still recover what arrived — the assembled text and token counts so far — and
 * record a partial usage record. The spend is never silently lost.
 */
public final class StreamAccumulator {

    private final StringBuilder content = new StringBuilder();
    private String id;
    private String model;
    private int inputTokens;
    private int outputTokens;
    private boolean complete;

    void start(String startId, String startModel, int startInputTokens) {
        this.id = startId;
        this.model = startModel;
        this.inputTokens = startInputTokens;
    }

    void appendText(String text) {
        content.append(text);
    }

    void outputTokens(int tokens) {
        this.outputTokens = tokens;
    }

    void markComplete() {
        this.complete = true;
    }

    /**
     * @return an immutable snapshot of what has accumulated so far
     */
    public StreamResult toResult() {
        return new StreamResult(id, model, content.toString(), inputTokens, outputTokens, complete);
    }
}

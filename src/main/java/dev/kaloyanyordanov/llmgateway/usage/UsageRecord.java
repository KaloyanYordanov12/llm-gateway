package dev.kaloyanyordanov.llmgateway.usage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One usage/cost record per billable (non-cached) provider call.
 */
@Entity
@Table(name = "usage_records")
public class UsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(nullable = false)
    private String model;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal cost;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean complete;

    /** Required by JPA. */
    protected UsageRecord() {
    }

    /**
     * Records a complete call.
     *
     * @param clientId     the client the usage is billed to
     * @param model        the model used
     * @param inputTokens  prompt tokens consumed
     * @param outputTokens completion tokens produced
     * @param cost         computed cost of the call
     */
    public UsageRecord(Long clientId, String model, int inputTokens, int outputTokens, BigDecimal cost) {
        this(clientId, model, inputTokens, outputTokens, cost, true);
    }

    /**
     * @param clientId     the client the usage is billed to
     * @param model        the model used
     * @param inputTokens  prompt tokens consumed
     * @param outputTokens completion tokens produced
     * @param cost         computed cost of the call
     * @param complete     whether the call completed (false for an aborted stream)
     */
    public UsageRecord(Long clientId, String model, int inputTokens, int outputTokens, BigDecimal cost,
            boolean complete) {
        this.clientId = clientId;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.cost = cost;
        this.complete = complete;
    }

    public Long getId() {
        return id;
    }

    public Long getClientId() {
        return clientId;
    }

    public String getModel() {
        return model;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isComplete() {
        return complete;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}

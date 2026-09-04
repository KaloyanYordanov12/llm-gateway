package dev.kaloyanyordanov.llmgateway.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A client permitted to call the gateway. Authenticates via the {@code x-api-key}
 * header; only the bcrypt hash of the key is persisted, never the raw key.
 *
 * <p>Multi-tenant controls are optional: {@code rateLimit} is {@code null} to use
 * the global default limit, and {@code budget} is {@code null} for no spend cap.
 * A client with both unset behaves exactly as before these columns existed.</p>
 */
@Entity
@Table(name = "clients")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "api_key_hash", nullable = false)
    private String apiKeyHash;

    @Column(nullable = false)
    private boolean enabled;

    /** Per-client request-per-refill-period cap; {@code null} uses the global default. */
    @Column(name = "rate_limit")
    private Integer rateLimit;

    /** Hard cumulative spend cap; {@code null} means no cap. */
    @Column(precision = 18, scale = 8)
    private BigDecimal budget;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA. */
    protected Client() {
    }

    /**
     * Creates a client with no per-client limit or budget (global default limit,
     * no spend cap).
     *
     * @param name       human-readable client name
     * @param apiKeyHash bcrypt hash of the client's API key
     * @param enabled    whether the client may authenticate
     */
    public Client(String name, String apiKeyHash, boolean enabled) {
        this(name, apiKeyHash, enabled, null, null);
    }

    /**
     * Creates a client with optional multi-tenant controls.
     *
     * @param name       human-readable client name
     * @param apiKeyHash bcrypt hash of the client's API key
     * @param enabled    whether the client may authenticate
     * @param rateLimit  per-client request cap, or {@code null} for the global default
     * @param budget     hard spend cap, or {@code null} for no cap
     */
    public Client(String name, String apiKeyHash, boolean enabled, Integer rateLimit, BigDecimal budget) {
        this.name = name;
        this.apiKeyHash = apiKeyHash;
        this.enabled = enabled;
        this.rateLimit = rateLimit;
        this.budget = budget;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getApiKeyHash() {
        return apiKeyHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Integer getRateLimit() {
        return rateLimit;
    }

    public BigDecimal getBudget() {
        return budget;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setRateLimit(Integer rateLimit) {
        this.rateLimit = rateLimit;
    }

    public void setBudget(BigDecimal budget) {
        this.budget = budget;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

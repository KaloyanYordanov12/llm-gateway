package dev.kaloyanyordanov.llmgateway.management;

import java.math.BigDecimal;

/**
 * Admin request to update a client's multi-tenant controls. Each field is applied
 * only when present; a {@code null} leaves that setting unchanged.
 *
 * @param rateLimit new per-client rate limit, or {@code null} to leave unchanged
 * @param budget    new hard spend cap, or {@code null} to leave unchanged
 * @param enabled   new enabled flag, or {@code null} to leave unchanged
 */
public record UpdateClientRequest(Integer rateLimit, BigDecimal budget, Boolean enabled) {
}

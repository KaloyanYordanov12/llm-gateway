package dev.kaloyanyordanov.llmgateway.management;

import java.math.BigDecimal;

/**
 * Admin request to update a client's multi-tenant controls. A value field is
 * applied only when present; a {@code null} leaves that setting unchanged. To
 * remove a setting (back to "no cap" / "global default"), send the matching clear
 * flag as {@code true}: a clear takes precedence over a value for the same field.
 *
 * @param rateLimit       new per-client rate limit, or {@code null} to leave unchanged
 * @param budget          new hard spend cap, or {@code null} to leave unchanged
 * @param enabled         new enabled flag, or {@code null} to leave unchanged
 * @param clearRateLimit  when {@code true}, reset the rate limit to the global default
 * @param clearBudget     when {@code true}, remove the budget cap (no cap)
 */
public record UpdateClientRequest(
        Integer rateLimit,
        BigDecimal budget,
        Boolean enabled,
        Boolean clearRateLimit,
        Boolean clearBudget) {
}

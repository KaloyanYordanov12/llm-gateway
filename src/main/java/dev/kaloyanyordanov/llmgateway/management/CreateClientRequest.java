package dev.kaloyanyordanov.llmgateway.management;

import java.math.BigDecimal;

/**
 * Admin request to create a client. Only the name is required; a null rate limit
 * uses the global default and a null budget means no spend cap.
 *
 * @param name      client name (must be non-blank and unique)
 * @param rateLimit optional per-client rate limit
 * @param budget    optional hard spend cap
 */
public record CreateClientRequest(String name, Integer rateLimit, BigDecimal budget) {
}

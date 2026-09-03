package dev.kaloyanyordanov.llmgateway.usage;

import java.math.BigDecimal;

/**
 * Aggregated usage totals for a client.
 *
 * @param clientId          the client
 * @param totalInputTokens  summed prompt tokens
 * @param totalOutputTokens summed completion tokens
 * @param totalCost         summed cost
 * @param requestCount      number of billable requests
 */
public record UsageTotals(
        Long clientId,
        long totalInputTokens,
        long totalOutputTokens,
        BigDecimal totalCost,
        long requestCount) {
}

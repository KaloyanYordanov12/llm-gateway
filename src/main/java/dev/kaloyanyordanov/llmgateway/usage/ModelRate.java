package dev.kaloyanyordanov.llmgateway.usage;

import java.math.BigDecimal;

/**
 * Per-model pricing, expressed as cost per 1,000 tokens.
 *
 * @param inputPer1k  cost per 1,000 input (prompt) tokens
 * @param outputPer1k cost per 1,000 output (completion) tokens
 */
public record ModelRate(BigDecimal inputPer1k, BigDecimal outputPer1k) {
}

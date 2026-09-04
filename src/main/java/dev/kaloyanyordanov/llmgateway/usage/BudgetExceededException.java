package dev.kaloyanyordanov.llmgateway.usage;

import java.math.BigDecimal;

/**
 * Thrown when a client's accumulated spend has reached its hard budget cap, so a
 * further billable request must be rejected before any provider call. Mapped to
 * {@code 402 Payment Required} ({@code budget_exceeded}) in the error envelope.
 */
public class BudgetExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param spent  the client's accumulated spend
     * @param budget the client's hard budget cap
     */
    public BudgetExceededException(BigDecimal spent, BigDecimal budget) {
        super("Budget exceeded: spent " + spent.toPlainString()
                + " has reached the cap of " + budget.toPlainString());
    }
}

package dev.kaloyanyordanov.llmgateway.usage;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/**
 * Prices requests from the configured {@link PricingProperties} table, which
 * doubles as the model allowlist. Unknown models are rejected (fail-secure).
 */
@Service
public class PricingService {

    private static final BigDecimal ONE_THOUSAND = BigDecimal.valueOf(1000);

    private final PricingProperties pricing;

    public PricingService(PricingProperties pricing) {
        this.pricing = pricing;
    }

    /**
     * @param model model id (may be null)
     * @return whether the model is in the pricing allowlist
     */
    public boolean isKnown(String model) {
        return model != null && pricing.models().containsKey(model);
    }

    /**
     * Computes the cost of a call.
     *
     * @param model        the model id
     * @param inputTokens  prompt tokens consumed
     * @param outputTokens completion tokens produced
     * @return the total cost
     * @throws UnknownModelException if the model is not in the pricing allowlist
     */
    public BigDecimal cost(String model, int inputTokens, int outputTokens) {
        ModelRate rate = pricing.models().get(model);
        if (rate == null) {
            throw new UnknownModelException(model);
        }
        BigDecimal inputCost = rate.inputPer1k().multiply(thousandsOf(inputTokens));
        BigDecimal outputCost = rate.outputPer1k().multiply(thousandsOf(outputTokens));
        return inputCost.add(outputCost);
    }

    private static BigDecimal thousandsOf(int tokens) {
        // tokens/1000 always terminates (1000 = 2^3 * 5^3), so this is exact.
        return BigDecimal.valueOf(tokens).divide(ONE_THOUSAND);
    }
}

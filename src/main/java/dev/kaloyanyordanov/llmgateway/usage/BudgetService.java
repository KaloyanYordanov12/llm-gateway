package dev.kaloyanyordanov.llmgateway.usage;

import dev.kaloyanyordanov.llmgateway.metrics.GatewayMetrics;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/**
 * Enforces per-client hard budget caps. A client with no cap ({@code null}
 * budget) is never rejected — today's behaviour. When a cap is set, a request is
 * admitted only while accumulated spend is strictly below it; once spend reaches
 * or exceeds the cap the request is rejected with {@link BudgetExceededException}
 * before any provider call, so the cap is never overspent.
 */
@Service
public class BudgetService {

    private final UsageService usageService;
    private final GatewayMetrics metrics;

    public BudgetService(UsageService usageService, GatewayMetrics metrics) {
        this.usageService = usageService;
        this.metrics = metrics;
    }

    /**
     * Rejects the request if the client is at or over its budget cap.
     *
     * @param clientId the authenticated client's id
     * @param budget   the client's hard budget cap, or {@code null} for no cap
     * @throws BudgetExceededException if accumulated spend has reached the cap
     */
    public void enforce(long clientId, BigDecimal budget) {
        if (budget == null) {
            return;
        }
        BigDecimal spent = usageService.totalsForClient(clientId).totalCost();
        if (spent.compareTo(budget) >= 0) {
            metrics.budgetRejection(clientId);
            throw new BudgetExceededException(spent, budget);
        }
    }
}

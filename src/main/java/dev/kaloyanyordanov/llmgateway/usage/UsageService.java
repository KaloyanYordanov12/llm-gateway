package dev.kaloyanyordanov.llmgateway.usage;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Records per-request usage and answers per-client aggregate queries. One record
 * is written per billable (non-cached) call, so totals never double-count.
 */
@Service
public class UsageService {

    private final UsageRepository repository;

    public UsageService(UsageRepository repository) {
        this.repository = repository;
    }

    /**
     * Persists a usage record.
     *
     * @param clientId     billed client
     * @param model        model used
     * @param inputTokens  prompt tokens
     * @param outputTokens completion tokens
     * @param cost         computed cost
     * @return the saved record
     */
    public UsageRecord record(Long clientId, String model, int inputTokens, int outputTokens, BigDecimal cost) {
        return repository.save(new UsageRecord(clientId, model, inputTokens, outputTokens, cost));
    }

    /**
     * @param clientId the client
     * @return exact summed totals for the client (zeros if none)
     */
    public UsageTotals totalsForClient(Long clientId) {
        return totals(clientId, repository.findByClientId(clientId));
    }

    /**
     * @return exact summed totals across all clients ({@code clientId} is null)
     */
    public UsageTotals overallTotals() {
        return totals(null, repository.findAll());
    }

    private static UsageTotals totals(Long clientId, List<UsageRecord> records) {
        long inputTokens = records.stream().mapToLong(UsageRecord::getInputTokens).sum();
        long outputTokens = records.stream().mapToLong(UsageRecord::getOutputTokens).sum();
        BigDecimal cost = records.stream()
                .map(UsageRecord::getCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new UsageTotals(clientId, inputTokens, outputTokens, cost, records.size());
    }
}

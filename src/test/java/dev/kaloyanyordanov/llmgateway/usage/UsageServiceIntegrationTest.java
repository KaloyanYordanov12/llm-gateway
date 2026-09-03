package dev.kaloyanyordanov.llmgateway.usage;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.llmgateway.AbstractPostgresIntegrationTest;
import dev.kaloyanyordanov.llmgateway.auth.Client;
import dev.kaloyanyordanov.llmgateway.auth.ClientRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UsageServiceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private UsageService usageService;

    @Autowired
    private UsageRepository usageRepository;

    @Autowired
    private ClientRepository clientRepository;

    private Long clientA;
    private Long clientB;

    @BeforeEach
    void seed() {
        usageRepository.deleteAll();
        clientRepository.deleteAll();
        clientA = clientRepository.save(new Client("a", "h", true)).getId();
        clientB = clientRepository.save(new Client("b", "h", true)).getId();
    }

    @Test
    void totalsSumExactlyAndPersistTimestamp() {
        usageService.record(clientA, "m", 100, 40, new BigDecimal("0.10"));
        UsageRecord second = usageService.record(clientA, "m", 200, 60, new BigDecimal("0.30"));

        assertThat(second.getCreatedAt()).isNotNull();

        UsageTotals totals = usageService.totalsForClient(clientA);
        assertThat(totals.totalInputTokens()).isEqualTo(300);
        assertThat(totals.totalOutputTokens()).isEqualTo(100);
        assertThat(totals.totalCost()).isEqualByComparingTo("0.40");
        assertThat(totals.requestCount()).isEqualTo(2);
    }

    @Test
    void perClientTalliesAreIsolated() {
        usageService.record(clientA, "m", 100, 40, new BigDecimal("0.10"));
        usageService.record(clientB, "m", 999, 999, new BigDecimal("9.99"));

        UsageTotals totalsA = usageService.totalsForClient(clientA);
        assertThat(totalsA.totalInputTokens()).isEqualTo(100);
        assertThat(totalsA.totalCost()).isEqualByComparingTo("0.10");
        assertThat(totalsA.requestCount()).isEqualTo(1);
    }
}

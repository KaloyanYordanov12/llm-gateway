package dev.kaloyanyordanov.llmgateway.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.kaloyanyordanov.llmgateway.auth.Client;
import dev.kaloyanyordanov.llmgateway.auth.ClientRepository;
import dev.kaloyanyordanov.llmgateway.usage.UsageService;
import dev.kaloyanyordanov.llmgateway.usage.UsageTotals;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ManagementControllerTest {

    private final ClientRepository clientRepository = mock(ClientRepository.class);
    private final UsageService usageService = mock(UsageService.class);
    private final StatsService statsService = mock(StatsService.class);
    private final ManagementController controller =
            new ManagementController(clientRepository, usageService, statsService);

    @Test
    void clientsMapsEntitiesToViewsWithoutKeyHash() {
        when(clientRepository.findAll()).thenReturn(List.of(new Client("acme", "bcrypt-hash", true)));

        List<ClientView> views = controller.clients();

        assertThat(views).hasSize(1);
        assertThat(views.get(0).name()).isEqualTo("acme");
        assertThat(views.get(0).enabled()).isTrue();
        // ClientView has no hash component, so the secret cannot leak.
    }

    @Test
    void usageDelegatesToService() {
        UsageTotals totals = new UsageTotals(5L, 10, 3, new BigDecimal("0.10"), 2);
        when(usageService.totalsForClient(5L)).thenReturn(totals);

        assertThat(controller.usage(5L)).isSameAs(totals);
    }

    @Test
    void statsDelegatesToService() {
        StatsView stats = new StatsView(3, 100, 40, new BigDecimal("1.50"), 7, 2, 1);
        when(statsService.currentStats()).thenReturn(stats);

        assertThat(controller.stats()).isSameAs(stats);
    }
}

package dev.kaloyanyordanov.llmgateway.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.kaloyanyordanov.llmgateway.auth.Client;
import dev.kaloyanyordanov.llmgateway.auth.ClientRepository;
import dev.kaloyanyordanov.llmgateway.usage.UsageService;
import dev.kaloyanyordanov.llmgateway.usage.UsageTotals;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ManagementControllerTest {

    private final ClientRepository clientRepository = mock(ClientRepository.class);
    private final UsageService usageService = mock(UsageService.class);
    private final StatsService statsService = mock(StatsService.class);
    private final ClientManagementService clientManagementService = mock(ClientManagementService.class);
    private final ManagementController controller =
            new ManagementController(clientRepository, usageService, statsService, clientManagementService);

    @Test
    void clientsMapsEntitiesToViewsWithoutKeyHash() {
        when(clientRepository.findAll())
                .thenReturn(List.of(new Client("acme", "bcrypt-hash", true, 30, new BigDecimal("2.00"))));

        List<ClientView> views = controller.clients();

        assertThat(views).hasSize(1);
        assertThat(views.get(0).name()).isEqualTo("acme");
        assertThat(views.get(0).enabled()).isTrue();
        assertThat(views.get(0).rateLimit()).isEqualTo(30);
        assertThat(views.get(0).budget()).isEqualByComparingTo("2.00");
        // ClientView has no hash component, so the secret cannot leak.
    }

    @Test
    void createDelegatesToServiceAndRevealsRawKeyOnce() {
        CreatedClientView created =
                new CreatedClientView(1L, "acme", true, 30, new BigDecimal("2.00"), "sk-gw-raw");
        when(clientManagementService.create("acme", 30, new BigDecimal("2.00"))).thenReturn(created);

        ResponseEntity<CreatedClientView> response =
                controller.createClient(new CreateClientRequest("acme", 30, new BigDecimal("2.00")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().apiKey()).isEqualTo("sk-gw-raw");
        assertThat(response.getBody().name()).isEqualTo("acme");
        assertThat(response.getBody().rateLimit()).isEqualTo(30);
    }

    @Test
    void updateDelegatesToServiceAndReturnsViewWithoutKey() {
        Client updated = new Client("acme", "bcrypt-hash", false, 10, new BigDecimal("5.00"));
        when(clientManagementService.update(eq(5L), eq(10), eq(new BigDecimal("5.00")), eq(false)))
                .thenReturn(updated);

        ClientView view =
                controller.updateClient(5L, new UpdateClientRequest(10, new BigDecimal("5.00"), false));

        assertThat(view.name()).isEqualTo("acme");
        assertThat(view.enabled()).isFalse();
        assertThat(view.rateLimit()).isEqualTo(10);
        assertThat(view.budget()).isEqualByComparingTo("5.00");
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

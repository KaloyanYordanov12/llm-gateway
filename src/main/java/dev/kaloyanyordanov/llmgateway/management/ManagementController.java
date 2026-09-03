package dev.kaloyanyordanov.llmgateway.management;

import dev.kaloyanyordanov.llmgateway.auth.ClientRepository;
import dev.kaloyanyordanov.llmgateway.usage.UsageService;
import dev.kaloyanyordanov.llmgateway.usage.UsageTotals;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only management API ({@code /api/*}), guarded by the admin-key filter.
 * Exposes clients, per-client usage, and aggregate stats for the dashboard.
 */
@RestController
@RequestMapping("/api")
public class ManagementController {

    private final ClientRepository clientRepository;
    private final UsageService usageService;
    private final StatsService statsService;

    public ManagementController(ClientRepository clientRepository, UsageService usageService,
            StatsService statsService) {
        this.clientRepository = clientRepository;
        this.usageService = usageService;
        this.statsService = statsService;
    }

    @GetMapping("/clients")
    public List<ClientView> clients() {
        return clientRepository.findAll().stream().map(ClientView::from).toList();
    }

    @GetMapping("/usage")
    public UsageTotals usage(@RequestParam("client") long clientId) {
        return usageService.totalsForClient(clientId);
    }

    @GetMapping("/stats")
    public StatsView stats() {
        return statsService.currentStats();
    }
}

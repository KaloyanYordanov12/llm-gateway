package dev.kaloyanyordanov.llmgateway.management;

import dev.kaloyanyordanov.llmgateway.auth.ClientRepository;
import dev.kaloyanyordanov.llmgateway.usage.UsageService;
import dev.kaloyanyordanov.llmgateway.usage.UsageTotals;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Management API ({@code /api/*}), guarded by the admin-key filter. Read endpoints
 * expose clients, per-client usage, and aggregate stats for the dashboard; write
 * endpoints create clients (revealing the raw key once) and update a client's
 * multi-tenant controls. A client {@code x-api-key} can never reach these routes —
 * only the admin key passes the filter.
 */
@RestController
@RequestMapping("/api")
public class ManagementController {

    private final ClientRepository clientRepository;
    private final UsageService usageService;
    private final StatsService statsService;
    private final ClientManagementService clientManagementService;

    public ManagementController(ClientRepository clientRepository, UsageService usageService,
            StatsService statsService, ClientManagementService clientManagementService) {
        this.clientRepository = clientRepository;
        this.usageService = usageService;
        this.statsService = statsService;
        this.clientManagementService = clientManagementService;
    }

    @GetMapping("/clients")
    public List<ClientView> clients() {
        return clientRepository.findAll().stream().map(ClientView::from).toList();
    }

    @PostMapping("/clients")
    public ResponseEntity<CreatedClientView> createClient(@RequestBody CreateClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(clientManagementService.create(request.name(), request.rateLimit(), request.budget()));
    }

    @PatchMapping("/clients/{id}")
    public ClientView updateClient(@PathVariable long id, @RequestBody UpdateClientRequest request) {
        return ClientView.from(
                clientManagementService.update(id, request.rateLimit(), request.budget(), request.enabled()));
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

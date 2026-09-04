package dev.kaloyanyordanov.llmgateway.proxy;

import dev.kaloyanyordanov.llmgateway.auth.ApiKeyAuthFilter;
import dev.kaloyanyordanov.llmgateway.auth.Client;
import dev.kaloyanyordanov.llmgateway.proxy.streaming.StreamingProxyService;
import dev.kaloyanyordanov.llmgateway.usage.BudgetService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The proxy endpoint. Authentication and rate limiting are enforced by servlet
 * filters before this handler runs; the authenticated client is read from the
 * request attribute set by {@link ApiKeyAuthFilter}. The client's hard budget cap
 * is enforced here, before any cache lookup or provider call, so an over-budget
 * client is rejected with {@code 402} and never overspends. A {@code stream:true}
 * request returns an SSE stream (via {@link StreamingProxyService}); otherwise the
 * non-streaming path ({@link ProxyService}) returns a JSON response unchanged.
 */
@RestController
public class ProxyController {

    private final ProxyService proxyService;
    private final StreamingProxyService streamingProxyService;
    private final BudgetService budgetService;

    public ProxyController(ProxyService proxyService, StreamingProxyService streamingProxyService,
            BudgetService budgetService) {
        this.proxyService = proxyService;
        this.streamingProxyService = streamingProxyService;
        this.budgetService = budgetService;
    }

    @PostMapping(path = "/v1/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object createMessage(@RequestBody MessagesRequest request, HttpServletRequest httpRequest) {
        Client client = (Client) httpRequest.getAttribute(ApiKeyAuthFilter.CLIENT_ATTRIBUTE);
        budgetService.enforce(client.getId(), client.getBudget());
        if (request.isStreaming()) {
            return streamingProxyService.stream(request, client.getId());
        }
        return proxyService.handle(request, client.getId());
    }
}

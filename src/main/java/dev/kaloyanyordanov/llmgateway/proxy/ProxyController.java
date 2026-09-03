package dev.kaloyanyordanov.llmgateway.proxy;

import dev.kaloyanyordanov.llmgateway.auth.ApiKeyAuthFilter;
import dev.kaloyanyordanov.llmgateway.auth.Client;
import dev.kaloyanyordanov.llmgateway.proxy.streaming.StreamingProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The proxy endpoint. Authentication and rate limiting are enforced by servlet
 * filters before this handler runs; the authenticated client is read from the
 * request attribute set by {@link ApiKeyAuthFilter}. A {@code stream:true} request
 * returns an SSE stream (via {@link StreamingProxyService}); otherwise the existing
 * non-streaming path ({@link ProxyService}) returns a JSON response unchanged.
 */
@RestController
public class ProxyController {

    private final ProxyService proxyService;
    private final StreamingProxyService streamingProxyService;

    public ProxyController(ProxyService proxyService, StreamingProxyService streamingProxyService) {
        this.proxyService = proxyService;
        this.streamingProxyService = streamingProxyService;
    }

    @PostMapping(path = "/v1/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object createMessage(@RequestBody MessagesRequest request, HttpServletRequest httpRequest) {
        Client client = (Client) httpRequest.getAttribute(ApiKeyAuthFilter.CLIENT_ATTRIBUTE);
        if (request.isStreaming()) {
            return streamingProxyService.stream(request, client.getId());
        }
        return proxyService.handle(request, client.getId());
    }
}

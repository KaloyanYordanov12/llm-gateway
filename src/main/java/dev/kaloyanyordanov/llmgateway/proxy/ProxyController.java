package dev.kaloyanyordanov.llmgateway.proxy;

import dev.kaloyanyordanov.llmgateway.provider.ProviderRegistry;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The proxy endpoint. Authentication and rate limiting are enforced by servlet
 * filters before this handler runs; here the request is forwarded to the provider
 * and its response returned. Caching (Phase 4) and usage accounting (Phase 5)
 * layer in around this call.
 */
@RestController
public class ProxyController {

    private final ProviderRegistry providerRegistry;

    public ProxyController(ProviderRegistry providerRegistry) {
        this.providerRegistry = providerRegistry;
    }

    @PostMapping(path = "/v1/messages",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public MessagesResponse createMessage(@RequestBody MessagesRequest request) {
        return providerRegistry.getDefault().createMessage(request);
    }
}

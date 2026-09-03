package dev.kaloyanyordanov.llmgateway.proxy;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The proxy endpoint. Authentication and rate limiting are enforced by servlet
 * filters before this handler runs; the actual cache/provider orchestration lives
 * in {@link ProxyService}.
 */
@RestController
public class ProxyController {

    private final ProxyService proxyService;

    public ProxyController(ProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @PostMapping(path = "/v1/messages",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public MessagesResponse createMessage(@RequestBody MessagesRequest request) {
        return proxyService.handle(request);
    }
}

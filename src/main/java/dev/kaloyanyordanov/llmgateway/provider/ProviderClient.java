package dev.kaloyanyordanov.llmgateway.provider;

import dev.kaloyanyordanov.llmgateway.proxy.MessagesRequest;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesResponse;

/**
 * An LLM provider the gateway can forward requests to. Multiple providers may be
 * registered (see {@link ProviderRegistry}); v2 adds failover/routing across
 * them. v1 calls one provider synchronously (streaming is v2).
 */
public interface ProviderClient {

    /**
     * @return the unique provider name (e.g. {@code anthropic})
     */
    String name();

    /**
     * Forwards a messages request to the provider and returns its response.
     *
     * @param request the inbound messages request
     * @return the provider's messages response
     */
    MessagesResponse createMessage(MessagesRequest request);
}

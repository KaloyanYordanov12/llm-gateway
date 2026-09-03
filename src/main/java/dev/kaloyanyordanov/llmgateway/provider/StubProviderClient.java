package dev.kaloyanyordanov.llmgateway.provider;

import dev.kaloyanyordanov.llmgateway.proxy.MessagesRequest;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesResponse;
import dev.kaloyanyordanov.llmgateway.proxy.Usage;

/**
 * A provider that returns a fixed, canned response and never makes a network
 * call. Selected in {@code demo} mode so a public deployment cannot cost anything
 * regardless of who calls it. It still returns a plausible small {@link Usage} so
 * accounting and the dashboard populate, and it flows through the same
 * {@code ProxyService} path (cache + usage) as the live provider.
 */
public class StubProviderClient implements ProviderClient {

    /** Stable provider name used to select this client as the registry default. */
    public static final String STUB_NAME = "stub";

    private static final String CANNED_TEXT =
            "This is a canned response from the LLM Gateway demo stub provider. "
                    + "No real model was called.";

    @Override
    public String name() {
        return STUB_NAME;
    }

    @Override
    public MessagesResponse createMessage(MessagesRequest request) {
        return new MessagesResponse(
                "msg_demo_stub",
                "message",
                "assistant",
                request.model(),
                CANNED_TEXT,
                "end_turn",
                new Usage(8, 24));
    }
}

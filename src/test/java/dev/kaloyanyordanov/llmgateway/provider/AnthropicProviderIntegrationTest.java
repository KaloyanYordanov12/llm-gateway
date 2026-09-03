package dev.kaloyanyordanov.llmgateway.provider;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.kaloyanyordanov.llmgateway.AbstractPostgresIntegrationTest;
import dev.kaloyanyordanov.llmgateway.proxy.Message;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesRequest;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class AnthropicProviderIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final WireMockServer WIREMOCK = new WireMockServer(options().dynamicPort());

    static {
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        registry.add("gateway.provider.base-url", WIREMOCK::baseUrl);
    }

    @Autowired
    private ProviderRegistry providerRegistry;

    @BeforeEach
    void resetStubs() {
        WIREMOCK.resetAll();
    }

    private static MessagesRequest sampleRequest() {
        return new MessagesRequest(
                "claude-x", null, List.of(new Message("user", "hi")), null, null, 100, null);
    }

    @Test
    void createMessageForwardsHeadersAndMapsSnakeCaseResponse() {
        WIREMOCK.stubFor(post(urlEqualTo("/v1/messages")).willReturn(okJson("""
                {
                  "id": "msg_01",
                  "type": "message",
                  "role": "assistant",
                  "model": "claude-x",
                  "content": "Hello!",
                  "stop_reason": "end_turn",
                  "usage": {"input_tokens": 12, "output_tokens": 7}
                }""")));

        MessagesResponse response = providerRegistry.getDefault().createMessage(sampleRequest());

        assertThat(response.id()).isEqualTo("msg_01");
        assertThat(response.content()).isEqualTo("Hello!");
        assertThat(response.stopReason()).isEqualTo("end_turn");
        assertThat(response.usage().inputTokens()).isEqualTo(12);
        assertThat(response.usage().outputTokens()).isEqualTo(7);

        WIREMOCK.verify(postRequestedFor(urlEqualTo("/v1/messages"))
                .withHeader("x-api-key", equalTo("test-provider-key"))
                .withHeader("anthropic-version", equalTo("2023-06-01")));
    }
}

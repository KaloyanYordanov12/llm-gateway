package dev.kaloyanyordanov.llmgateway.provider.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.kaloyanyordanov.llmgateway.AbstractPostgresIntegrationTest;
import dev.kaloyanyordanov.llmgateway.provider.ProviderRegistry;
import dev.kaloyanyordanov.llmgateway.proxy.Message;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesRequest;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class OpenAiProviderIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final WireMockServer WIREMOCK = new WireMockServer(options().dynamicPort());

    static {
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void openAiProperties(DynamicPropertyRegistry registry) {
        registry.add("gateway.provider.openai.base-url", WIREMOCK::baseUrl);
    }

    @Autowired
    private ProviderRegistry providerRegistry;

    @BeforeEach
    void resetStubs() {
        WIREMOCK.resetAll();
    }

    @Test
    void mapsOpenAiChatCompletionToNormalizedResponse() {
        WIREMOCK.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(okJson("""
                {
                  "id": "chatcmpl-1",
                  "model": "gpt-4o",
                  "choices": [
                    {"index": 0,
                     "message": {"role": "assistant", "content": "Hi from OpenAI"},
                     "finish_reason": "stop"}
                  ],
                  "usage": {"prompt_tokens": 11, "completion_tokens": 6, "total_tokens": 17}
                }""")));

        MessagesRequest request = new MessagesRequest(
                "gpt-4o", "be brief", List.of(new Message("user", "hi")), null, null, 100, null);

        MessagesResponse response = providerRegistry.get("openai").createMessage(request);

        assertThat(response.id()).isEqualTo("chatcmpl-1");
        assertThat(response.content()).isEqualTo("Hi from OpenAI");
        assertThat(response.stopReason()).isEqualTo("stop");
        assertThat(response.usage().inputTokens()).isEqualTo(11);
        assertThat(response.usage().outputTokens()).isEqualTo(6);

        WIREMOCK.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer test-openai-key")));
    }
}

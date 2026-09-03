package dev.kaloyanyordanov.llmgateway.proxy.streaming;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import dev.kaloyanyordanov.llmgateway.AbstractPostgresIntegrationTest;
import dev.kaloyanyordanov.llmgateway.auth.ApiKeyAuthFilter;
import dev.kaloyanyordanov.llmgateway.auth.Client;
import dev.kaloyanyordanov.llmgateway.auth.ClientRepository;
import dev.kaloyanyordanov.llmgateway.usage.UsageRecord;
import dev.kaloyanyordanov.llmgateway.usage.UsageRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class StreamingIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String VALID_KEY = "secret-key";
    private static final String BODY =
            "{\"model\":\"claude-3-5-sonnet-20241022\",\"messages\":[{\"role\":\"user\","
            + "\"content\":\"stream-me\"}],\"max_tokens\":100,\"stream\":true}";

    private static final String SSE_BODY = String.join("\n",
            "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"model\":\"claude-3-5-sonnet-20241022\","
            + "\"usage\":{\"input_tokens\":10,\"output_tokens\":0}}}",
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}",
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\" world\"}}",
            "data: {\"type\":\"message_delta\",\"usage\":{\"output_tokens\":5}}",
            "data: {\"type\":\"message_stop\"}");

    private static final WireMockServer WIREMOCK = new WireMockServer(options().dynamicPort());

    static {
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void providerUrl(DynamicPropertyRegistry registry) {
        registry.add("gateway.provider.base-url", WIREMOCK::baseUrl);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private UsageRepository usageRepository;

    @BeforeEach
    void setUp() {
        WIREMOCK.resetAll();
        clientRepository.save(new Client("acme", passwordEncoder.encode(VALID_KEY), true));
    }

    private String stream() throws Exception {
        MvcResult started = mockMvc.perform(post("/v1/messages")
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void streamsAssemblesForwardsRecordsUsageAndCachesForReplay() throws Exception {
        WIREMOCK.stubFor(WireMock.post(urlEqualTo("/v1/messages")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withBody(SSE_BODY)));

        // First request streams from upstream: deltas forwarded, usage recorded complete.
        String first = stream();
        Assertions.assertThat(first).contains("Hello").contains("world").contains("done");
        Assertions.assertThat(usageRepository.findAll()).singleElement().satisfies(record -> {
            Assertions.assertThat(record.getInputTokens()).isEqualTo(10);
            Assertions.assertThat(record.getOutputTokens()).isEqualTo(5);
            Assertions.assertThat(record.isComplete()).isTrue();
        });

        // Second identical request is a cache hit: replayed from cache, no upstream call,
        // no new usage row.
        String second = stream();
        Assertions.assertThat(second).contains("Hello world");
        WIREMOCK.verify(1, postRequestedFor(urlEqualTo("/v1/messages")));
        Assertions.assertThat(usageRepository.findAll()).hasSize(1);
    }

    @Test
    void abortedStreamRecordsPartialUsageAndCachesNothing() throws Exception {
        // Upstream ends after one delta with no message_stop — an aborted stream.
        String truncated = String.join("\n",
                "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\","
                + "\"model\":\"claude-3-5-sonnet-20241022\",\"usage\":{\"input_tokens\":10,\"output_tokens\":0}}}",
                "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Partial\"}}");
        WIREMOCK.stubFor(WireMock.post(urlEqualTo("/v1/messages")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withBody(truncated)));

        String body = stream();
        Assertions.assertThat(body).contains("Partial").contains("error");

        // Partial usage recorded (flagged incomplete), nothing cached.
        Assertions.assertThat(usageRepository.findAll()).singleElement()
                .extracting(UsageRecord::isComplete).isEqualTo(false);
    }
}

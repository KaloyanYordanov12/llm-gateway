package dev.kaloyanyordanov.llmgateway.proxy;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import dev.kaloyanyordanov.llmgateway.AbstractPostgresIntegrationTest;
import dev.kaloyanyordanov.llmgateway.auth.ApiKeyAuthFilter;
import dev.kaloyanyordanov.llmgateway.auth.Client;
import dev.kaloyanyordanov.llmgateway.auth.ClientRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves the hard budget cap is enforced from the pricing table's computed cost —
 * no real provider, no real spend ($0). A request under the cap is served and
 * billed; once accumulated spend reaches the cap the next request is rejected with
 * {@code 402}; raising the cap re-enables the client. Prompts are made unique so
 * each billed call is a genuine cache miss (the response cache is a shared
 * singleton across tests).
 */
@AutoConfigureMockMvc
class BudgetIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String KEY = "budget-key";

    private static final WireMockServer WIREMOCK = new WireMockServer(options().dynamicPort());

    static {
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        registry.add("gateway.provider.base-url", WIREMOCK::baseUrl);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        WIREMOCK.resetAll();
        // sonnet: 0.003/1k input + 0.015/1k output => 1000+1000 tokens costs 0.018.
        WIREMOCK.stubFor(WireMock.post(urlEqualTo("/v1/messages")).willReturn(okJson("""
                {
                  "id": "msg_budget",
                  "type": "message",
                  "role": "assistant",
                  "model": "claude-x",
                  "content": "hi",
                  "stop_reason": "end_turn",
                  "usage": {"input_tokens": 1000, "output_tokens": 1000}
                }""")));
    }

    private static String uniqueBody() {
        return "{\"model\":\"claude-3-5-sonnet-20241022\",\"messages\":[{\"role\":\"user\","
                + "\"content\":\"" + UUID.randomUUID() + "\"}],\"max_tokens\":100}";
    }

    private void send(String key, String body, int expectedStatus) throws Exception {
        mockMvc.perform(post("/v1/messages")
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void rejectsWith402OnceSpendReachesTheCapThenReenablesWhenRaised() throws Exception {
        // Cap (0.01) is below the cost of one call (0.018).
        clientRepository.save(
                new Client("budgeted", passwordEncoder.encode(KEY), true, null, new BigDecimal("0.01")));

        // Under cap: served and billed 0.018 (unique prompt => genuine miss).
        send(KEY, uniqueBody(), 200);

        // Now over cap (0.018 >= 0.01): rejected before any provider call.
        mockMvc.perform(post("/v1/messages")
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(uniqueBody()))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.type").value("error"))
                .andExpect(jsonPath("$.error.type").value("budget_exceeded"))
                .andExpect(jsonPath("$.request_id").exists());

        // Raising the cap re-enables the client on the next request.
        Client client = clientRepository.findByName("budgeted").orElseThrow();
        client.setBudget(new BigDecimal("1.00"));
        clientRepository.save(client);

        send(KEY, uniqueBody(), 200);
    }

    @Test
    void aClientWithNoCapIsNeverBudgetRejected() throws Exception {
        clientRepository.save(new Client("uncapped", passwordEncoder.encode("uncapped-key"), true));

        for (int i = 0; i < 3; i++) {
            send("uncapped-key", uniqueBody(), 200);
        }
    }
}

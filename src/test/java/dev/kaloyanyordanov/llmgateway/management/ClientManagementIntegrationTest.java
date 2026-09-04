package dev.kaloyanyordanov.llmgateway.management;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.jayway.jsonpath.JsonPath;
import dev.kaloyanyordanov.llmgateway.AbstractPostgresIntegrationTest;
import dev.kaloyanyordanov.llmgateway.auth.ApiKeyAuthFilter;
import dev.kaloyanyordanov.llmgateway.auth.Client;
import dev.kaloyanyordanov.llmgateway.auth.ClientRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Admin create/update endpoints end to end: creating a client reveals the raw key
 * once and stores only its hash, the revealed key really authenticates on the
 * proxy, updates change a client's controls, and a client key can never reach the
 * write routes. No real provider — WireMock serves the proxy call ($0).
 */
@AutoConfigureMockMvc
class ClientManagementIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String ADMIN_KEY = "dev-admin-key";

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

    @BeforeEach
    void setUp() {
        WIREMOCK.resetAll();
        WIREMOCK.stubFor(WireMock.post(urlEqualTo("/v1/messages")).willReturn(okJson("""
                {
                  "id": "msg_created",
                  "type": "message",
                  "role": "assistant",
                  "model": "claude-x",
                  "content": "hi",
                  "stop_reason": "end_turn",
                  "usage": {"input_tokens": 1, "output_tokens": 1}
                }""")));
    }

    @Test
    void createReturnsTheRawKeyOnceStoresOnlyItsHashAndTheKeyAuthenticates() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/clients")
                        .header(AdminAuthFilter.ADMIN_KEY_HEADER, ADMIN_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"acme\",\"rate_limit\":30,\"budget\":\"2.00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("acme"))
                .andExpect(jsonPath("$.rate_limit").value(30))
                .andExpect(jsonPath("$.budget").exists())
                .andExpect(jsonPath("$.api_key").exists())
                .andExpect(jsonPath("$.api_key_hash").doesNotExist())
                .andReturn();

        String rawKey = JsonPath.read(result.getResponse().getContentAsString(), "$.api_key");
        assertThat(rawKey).startsWith("sk-gw-");

        // Only the hash is stored — the raw key is not persisted anywhere.
        Client stored = clientRepository.findByName("acme").orElseThrow();
        assertThat(stored.getApiKeyHash()).isNotEqualTo(rawKey);
        assertThat(stored.getRateLimit()).isEqualTo(30);
        assertThat(stored.getBudget()).isEqualByComparingTo("2.00");

        // The revealed key really authenticates on the proxy.
        mockMvc.perform(post("/v1/messages")
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uniqueBody()))
                .andExpect(status().isOk());
    }

    @Test
    void createdClientAppearsInTheClientListingWithItsControls() throws Exception {
        mockMvc.perform(post("/api/clients")
                        .header(AdminAuthFilter.ADMIN_KEY_HEADER, ADMIN_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"beta\",\"rate_limit\":15}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/clients").header(AdminAuthFilter.ADMIN_KEY_HEADER, ADMIN_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("beta"))
                .andExpect(jsonPath("$[0].rate_limit").value(15))
                .andExpect(jsonPath("$[0].api_key_hash").doesNotExist());
    }

    @Test
    void updateChangesBudgetAndEnabledForAClient() throws Exception {
        long id = clientRepository.save(new Client("gamma", "hash", true)).getId();

        mockMvc.perform(patch("/api/clients/" + id)
                        .header(AdminAuthFilter.ADMIN_KEY_HEADER, ADMIN_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"budget\":\"5.00\",\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budget").exists())
                .andExpect(jsonPath("$.enabled").value(false));

        Client updated = clientRepository.findById(id).orElseThrow();
        assertThat(updated.getBudget()).isEqualByComparingTo("5.00");
        assertThat(updated.isEnabled()).isFalse();
    }

    @Test
    void blankNameIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/api/clients")
                        .header(AdminAuthFilter.ADMIN_KEY_HEADER, ADMIN_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"));
    }

    @Test
    void createRequiresTheAdminKeyAndRejectsAClientKey() throws Exception {
        // No admin key.
        mockMvc.perform(post("/api/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"nope\"}"))
                .andExpect(status().isUnauthorized());

        // A client API key must not reach the admin write route.
        mockMvc.perform(post("/api/clients")
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, "some-client-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"nope\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(clientRepository.findByName("nope")).isEmpty();
    }

    @Test
    void updateRequiresTheAdminKey() throws Exception {
        long id = clientRepository.save(new Client("delta", "hash", true)).getId();

        mockMvc.perform(patch("/api/clients/" + id)
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, "some-client-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createWithAWrongAdminKeyIsRejected() throws Exception {
        mockMvc.perform(post("/api/clients")
                        .header(AdminAuthFilter.ADMIN_KEY_HEADER, "wrong-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"nope\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(clientRepository.findByName("nope")).isEmpty();
    }

    @Test
    void updateWithAMissingOrWrongAdminKeyIsRejected() throws Exception {
        long id = clientRepository.save(new Client("epsilon", "hash", true)).getId();

        // No admin key at all.
        mockMvc.perform(patch("/api/clients/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isUnauthorized());

        // A wrong admin key.
        mockMvc.perform(patch("/api/clients/" + id)
                        .header(AdminAuthFilter.ADMIN_KEY_HEADER, "wrong-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isUnauthorized());

        // The client is untouched by the rejected writes.
        assertThat(clientRepository.findById(id).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void duplicateNameIsRejectedWith400() throws Exception {
        clientRepository.save(new Client("acme", "hash", true));

        mockMvc.perform(post("/api/clients")
                        .header(AdminAuthFilter.ADMIN_KEY_HEADER, ADMIN_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"acme\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("acme")));
    }

    @Test
    void patchingAnUnknownClientIdIsRejectedWith400() throws Exception {
        mockMvc.perform(patch("/api/clients/999999")
                        .header(AdminAuthFilter.ADMIN_KEY_HEADER, ADMIN_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"));
    }

    private static String uniqueBody() {
        return "{\"model\":\"claude-3-5-sonnet-20241022\",\"messages\":[{\"role\":\"user\","
                + "\"content\":\"" + UUID.randomUUID() + "\"}],\"max_tokens\":10}";
    }
}

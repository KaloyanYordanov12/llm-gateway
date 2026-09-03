package dev.kaloyanyordanov.llmgateway.provider;

import dev.kaloyanyordanov.llmgateway.proxy.MessagesRequest;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesResponse;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * {@link ProviderClient} for Anthropic's Messages API, calling it over HTTP with
 * a blocking {@link RestClient}. The API key is sent as {@code x-api-key} and the
 * {@code anthropic-version} header is set per Anthropic's contract.
 */
public class AnthropicProviderClient implements ProviderClient {

    private static final String MESSAGES_PATH = "/v1/messages";
    private static final String API_KEY_HEADER = "x-api-key";
    private static final String VERSION_HEADER = "anthropic-version";

    private final String name;
    private final RestClient restClient;
    private final String apiKey;
    private final String anthropicVersion;

    /**
     * @param name             provider name
     * @param restClient       RestClient pre-configured with the provider base URL
     * @param apiKey           provider API key for the {@code x-api-key} header
     * @param anthropicVersion value for the {@code anthropic-version} header
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
            justification = "RestClient is an immutable, thread-safe Spring component; "
                    + "holding the shared instance is intentional, not mutable-state exposure.")
    public AnthropicProviderClient(String name, RestClient restClient, String apiKey,
            String anthropicVersion) {
        this.name = name;
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.anthropicVersion = anthropicVersion;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public MessagesResponse createMessage(MessagesRequest request) {
        return restClient.post()
                .uri(MESSAGES_PATH)
                .header(API_KEY_HEADER, apiKey)
                .header(VERSION_HEADER, anthropicVersion)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(MessagesResponse.class);
    }
}

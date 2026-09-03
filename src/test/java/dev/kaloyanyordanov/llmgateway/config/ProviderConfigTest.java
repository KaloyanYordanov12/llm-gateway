package dev.kaloyanyordanov.llmgateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.llmgateway.provider.AnthropicProviderClient;
import dev.kaloyanyordanov.llmgateway.provider.ProviderProperties;
import dev.kaloyanyordanov.llmgateway.provider.ProviderRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class ProviderConfigTest {

    private final ProviderConfig config = new ProviderConfig();
    private final ProviderProperties properties = new ProviderProperties(
            "anthropic", "http://localhost:8089", "test-provider-key", "2023-06-01");

    @Test
    void buildsRestClientAdapterAndRegistry() {
        RestClient restClient = config.providerRestClient(properties, RestClient.builder());
        assertThat(restClient).isNotNull();

        AnthropicProviderClient client = config.anthropicProviderClient(properties, restClient);
        assertThat(client.name()).isEqualTo("anthropic");

        ProviderRegistry registry = config.providerRegistry(List.of(client), properties);
        assertThat(registry.getDefault()).isSameAs(client);
    }
}

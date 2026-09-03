package dev.kaloyanyordanov.llmgateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.llmgateway.provider.ProviderClient;
import dev.kaloyanyordanov.llmgateway.provider.ProviderProperties;
import dev.kaloyanyordanov.llmgateway.provider.ProviderRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class ProviderConfigTest {

    private final ProviderConfig config = new ProviderConfig();
    private final ProviderProperties properties = new ProviderProperties(
            "anthropic", "http://localhost:8089", "test-provider-key", "2023-06-01");

    @Test
    void buildsRestClientResilientAdapterAndRegistry() {
        RestClient restClient = config.providerRestClient(properties, RestClient.builder());
        assertThat(restClient).isNotNull();

        ProviderClient client = config.anthropicProviderClient(
                properties, restClient, CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults());
        assertThat(client.name()).isEqualTo("anthropic");

        ProviderRegistry registry = config.providerRegistry(List.of(client), properties);
        assertThat(registry.getDefault()).isSameAs(client);
    }
}

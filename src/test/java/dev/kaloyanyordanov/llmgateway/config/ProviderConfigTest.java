package dev.kaloyanyordanov.llmgateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.llmgateway.provider.ProviderClient;
import dev.kaloyanyordanov.llmgateway.provider.ProviderMode;
import dev.kaloyanyordanov.llmgateway.provider.ProviderProperties;
import dev.kaloyanyordanov.llmgateway.provider.ProviderRegistry;
import dev.kaloyanyordanov.llmgateway.provider.StubProviderClient;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class ProviderConfigTest {

    private final ProviderConfig config = new ProviderConfig();

    private static ProviderProperties properties(ProviderMode mode) {
        return new ProviderProperties(
                mode, "anthropic", "http://localhost:8089", "test-provider-key", "2023-06-01");
    }

    @Test
    void buildsRestClientResilientAdapterAndRegistry() {
        ProviderProperties properties = properties(ProviderMode.LIVE);
        RestClient restClient = config.providerRestClient(properties, RestClient.builder());
        assertThat(restClient).isNotNull();

        ProviderClient anthropic = config.anthropicProviderClient(
                properties, restClient, CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults());
        assertThat(anthropic.name()).isEqualTo("anthropic");

        ProviderClient stub = config.stubProviderClient();
        assertThat(stub).isInstanceOf(StubProviderClient.class);

        // live mode defaults to the anthropic client
        ProviderRegistry registry = config.providerRegistry(List.of(anthropic, stub), properties);
        assertThat(registry.getDefault()).isSameAs(anthropic);
    }

    @Test
    void demoModeSelectsTheStubAsDefault() {
        ProviderProperties properties = properties(ProviderMode.DEMO);
        ProviderClient anthropic = config.anthropicProviderClient(
                properties, config.providerRestClient(properties, RestClient.builder()),
                CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults());
        ProviderClient stub = config.stubProviderClient();

        ProviderRegistry registry = config.providerRegistry(List.of(anthropic, stub), properties);

        assertThat(registry.getDefault()).isSameAs(stub);
    }

    @Test
    void resolveDefaultProviderNameFollowsMode() {
        assertThat(ProviderConfig.resolveDefaultProviderName(properties(ProviderMode.LIVE)))
                .isEqualTo("anthropic");
        assertThat(ProviderConfig.resolveDefaultProviderName(properties(ProviderMode.DEMO)))
                .isEqualTo("stub");
    }
}

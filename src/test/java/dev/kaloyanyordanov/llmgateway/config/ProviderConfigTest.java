package dev.kaloyanyordanov.llmgateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kaloyanyordanov.llmgateway.provider.FailoverProviderClient;
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
        return properties(mode, List.of());
    }

    private static ProviderProperties properties(ProviderMode mode, List<String> chain) {
        return new ProviderProperties(
                mode, chain, "anthropic", "http://localhost:8089", "test-provider-key", "2023-06-01",
                new ProviderProperties.OpenAi("openai", "http://localhost:8090", "test-openai-key"));
    }

    private ProviderClient openai(ProviderProperties properties) {
        return config.openAiProviderClient(
                properties, config.openAiRestClient(properties, RestClient.builder()),
                CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults());
    }

    private ProviderClient anthropic(ProviderProperties properties) {
        return config.anthropicProviderClient(
                properties, config.providerRestClient(properties, RestClient.builder()),
                CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults());
    }

    @Test
    void buildsRestClientResilientAdapterAndRegistry() {
        ProviderProperties properties = properties(ProviderMode.LIVE);
        RestClient restClient = config.providerRestClient(properties, RestClient.builder());
        assertThat(restClient).isNotNull();

        ProviderClient anthropic = anthropic(properties);
        assertThat(anthropic.name()).isEqualTo("anthropic");

        ProviderClient stub = config.stubProviderClient();
        assertThat(stub).isInstanceOf(StubProviderClient.class);

        // live mode defaults to the anthropic client
        ProviderRegistry registry = config.providerRegistry(List.of(anthropic, stub), properties);
        assertThat(registry.getDefault()).isSameAs(anthropic);
    }

    @Test
    void buildsResilientOpenAiClient() {
        ProviderProperties properties = properties(ProviderMode.LIVE);
        ProviderClient openai = config.openAiProviderClient(
                properties, config.openAiRestClient(properties, RestClient.builder()),
                CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults());

        assertThat(openai.name()).isEqualTo("openai");
    }

    @Test
    void demoModeSelectsTheStubAsDefault() {
        ProviderProperties properties = properties(ProviderMode.DEMO);
        ProviderClient anthropic = anthropic(properties);
        ProviderClient stub = config.stubProviderClient();

        ProviderRegistry registry = config.providerRegistry(List.of(anthropic, stub), properties);

        assertThat(registry.getDefault()).isSameAs(stub);
    }

    @Test
    void liveChainMakesFailoverTheDefault() {
        ProviderProperties properties = properties(ProviderMode.LIVE, List.of("anthropic", "openai"));
        ProviderRegistry registry = config.providerRegistry(
                List.of(anthropic(properties), openai(properties), config.stubProviderClient()), properties);

        assertThat(registry.getDefault()).isInstanceOf(FailoverProviderClient.class);
        assertThat(registry.getDefault().name()).isEqualTo("failover");
    }

    @Test
    void unknownProviderInChainFailsFast() {
        ProviderProperties properties = properties(ProviderMode.LIVE, List.of("anthropic", "ghost"));

        assertThatThrownBy(() -> config.providerRegistry(List.of(anthropic(properties)), properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void resolveDefaultProviderNameFollowsMode() {
        assertThat(ProviderConfig.resolveDefaultProviderName(properties(ProviderMode.LIVE)))
                .isEqualTo("anthropic");
        assertThat(ProviderConfig.resolveDefaultProviderName(properties(ProviderMode.DEMO)))
                .isEqualTo("stub");
    }
}

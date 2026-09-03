package dev.kaloyanyordanov.llmgateway.config;

import dev.kaloyanyordanov.llmgateway.provider.AnthropicProviderClient;
import dev.kaloyanyordanov.llmgateway.provider.ProviderClient;
import dev.kaloyanyordanov.llmgateway.provider.ProviderProperties;
import dev.kaloyanyordanov.llmgateway.provider.ProviderRegistry;
import dev.kaloyanyordanov.llmgateway.provider.ResilientProviderClient;
import dev.kaloyanyordanov.llmgateway.provider.StubProviderClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.net.http.HttpClient;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Wiring for provider access: the provider {@link RestClient} (base URL from
 * {@link ProviderProperties}, using the app's configured message converters), the
 * Anthropic adapter, and the {@link ProviderRegistry}.
 */
@Configuration
@EnableConfigurationProperties(ProviderProperties.class)
public class ProviderConfig {

    @Bean
    public RestClient providerRestClient(ProviderProperties properties, RestClient.Builder builder) {
        // Force HTTP/1.1: the JDK HttpClient's default h2c upgrade attempt over
        // cleartext HTTP fails against some servers (e.g. WireMock) with an EOF.
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }

    @Bean
    public ProviderClient anthropicProviderClient(
            ProviderProperties properties, RestClient providerRestClient,
            CircuitBreakerRegistry circuitBreakerRegistry, RetryRegistry retryRegistry) {
        AnthropicProviderClient delegate = new AnthropicProviderClient(
                properties.name(), providerRestClient, properties.apiKey(), properties.anthropicVersion());
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(properties.name());
        Retry retry = retryRegistry.retry(properties.name());
        return new ResilientProviderClient(delegate, circuitBreaker, retry);
    }

    @Bean
    public ProviderClient stubProviderClient() {
        return new StubProviderClient();
    }

    @Bean
    public ProviderRegistry providerRegistry(List<ProviderClient> providers, ProviderProperties properties) {
        return new ProviderRegistry(providers, resolveDefaultProviderName(properties));
    }

    /**
     * Resolves which registered provider is the registry default, based on
     * {@code gateway.provider.mode}. The switch is exhaustive over
     * {@link dev.kaloyanyordanov.llmgateway.provider.ProviderMode}; an unrecognized
     * mode value never reaches here because it fails property binding at startup.
     *
     * @param properties the provider properties
     * @return the provider name to serve as the registry default
     */
    static String resolveDefaultProviderName(ProviderProperties properties) {
        return switch (properties.mode()) {
            case LIVE -> properties.name();
            case DEMO -> StubProviderClient.STUB_NAME;
        };
    }
}


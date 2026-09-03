package dev.kaloyanyordanov.llmgateway.config;

import dev.kaloyanyordanov.llmgateway.provider.AnthropicProviderClient;
import dev.kaloyanyordanov.llmgateway.provider.ProviderClient;
import dev.kaloyanyordanov.llmgateway.provider.ProviderProperties;
import dev.kaloyanyordanov.llmgateway.provider.ProviderRegistry;
import dev.kaloyanyordanov.llmgateway.provider.ResilientProviderClient;
import dev.kaloyanyordanov.llmgateway.provider.StubProviderClient;
import dev.kaloyanyordanov.llmgateway.provider.openai.OpenAiProviderClient;
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
 * Wiring for provider access: per-provider {@link RestClient}s (base URL from
 * {@link ProviderProperties}, using the app's configured message converters), the
 * resilience-wrapped Anthropic and OpenAI adapters, the demo stub, and the
 * {@link ProviderRegistry}.
 */
@Configuration
@EnableConfigurationProperties(ProviderProperties.class)
public class ProviderConfig {

    @Bean
    public RestClient providerRestClient(ProviderProperties properties, RestClient.Builder builder) {
        return http1RestClient(builder, properties.baseUrl());
    }

    @Bean
    public RestClient openAiRestClient(ProviderProperties properties, RestClient.Builder builder) {
        return http1RestClient(builder, properties.openai().baseUrl());
    }

    @Bean
    public ProviderClient anthropicProviderClient(
            ProviderProperties properties, RestClient providerRestClient,
            CircuitBreakerRegistry circuitBreakerRegistry, RetryRegistry retryRegistry) {
        AnthropicProviderClient delegate = new AnthropicProviderClient(
                properties.name(), providerRestClient, properties.apiKey(), properties.anthropicVersion());
        return resilient(delegate, circuitBreakerRegistry, retryRegistry);
    }

    @Bean
    public ProviderClient openAiProviderClient(
            ProviderProperties properties, RestClient openAiRestClient,
            CircuitBreakerRegistry circuitBreakerRegistry, RetryRegistry retryRegistry) {
        OpenAiProviderClient delegate = new OpenAiProviderClient(
                properties.openai().name(), openAiRestClient, properties.openai().apiKey());
        return resilient(delegate, circuitBreakerRegistry, retryRegistry);
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

    private static ResilientProviderClient resilient(ProviderClient delegate,
            CircuitBreakerRegistry circuitBreakerRegistry, RetryRegistry retryRegistry) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(delegate.name());
        Retry retry = retryRegistry.retry(delegate.name());
        return new ResilientProviderClient(delegate, circuitBreaker, retry);
    }

    private static RestClient http1RestClient(RestClient.Builder builder, String baseUrl) {
        // Force HTTP/1.1: the JDK HttpClient's default h2c upgrade attempt over
        // cleartext HTTP fails against some servers (e.g. WireMock) with an EOF.
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        return builder
                .baseUrl(baseUrl)
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }
}

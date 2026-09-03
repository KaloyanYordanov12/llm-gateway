package dev.kaloyanyordanov.llmgateway.config;

import dev.kaloyanyordanov.llmgateway.provider.AnthropicProviderClient;
import dev.kaloyanyordanov.llmgateway.provider.ProviderClient;
import dev.kaloyanyordanov.llmgateway.provider.ProviderProperties;
import dev.kaloyanyordanov.llmgateway.provider.ProviderRegistry;
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
    public AnthropicProviderClient anthropicProviderClient(
            ProviderProperties properties, RestClient providerRestClient) {
        return new AnthropicProviderClient(
                properties.name(), providerRestClient, properties.apiKey(), properties.anthropicVersion());
    }

    @Bean
    public ProviderRegistry providerRegistry(List<ProviderClient> providers, ProviderProperties properties) {
        return new ProviderRegistry(providers, properties.name());
    }
}

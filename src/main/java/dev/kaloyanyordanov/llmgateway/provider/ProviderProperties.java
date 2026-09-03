package dev.kaloyanyordanov.llmgateway.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Provider connection settings. Defaults are safe, obviously-fake values pointing
 * at a local stub (WireMock) so nothing real is ever called; real values come
 * from env in production.
 *
 * @param mode             which provider to proxy to ({@code live} | {@code demo}, default {@code live})
 * @param name             the default provider name
 * @param baseUrl          provider base URL (a local stub by default)
 * @param apiKey           provider API key (a non-real placeholder by default)
 * @param anthropicVersion value for the {@code anthropic-version} header
 */
@ConfigurationProperties("gateway.provider")
public record ProviderProperties(
        @DefaultValue("live") ProviderMode mode,
        @DefaultValue("anthropic") String name,
        @DefaultValue("http://localhost:8089") String baseUrl,
        @DefaultValue("test-provider-key") String apiKey,
        @DefaultValue("2023-06-01") String anthropicVersion) {
}

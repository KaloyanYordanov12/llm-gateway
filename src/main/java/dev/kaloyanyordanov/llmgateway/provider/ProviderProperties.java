package dev.kaloyanyordanov.llmgateway.provider;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Provider connection settings. Defaults are safe, obviously-fake values pointing
 * at local stubs (WireMock) so nothing real is ever called; real values come from
 * env in production.
 *
 * <p>The primary (Anthropic) provider is configured by the flat {@code name} /
 * {@code baseUrl} / {@code apiKey} / {@code anthropicVersion} fields (v1 shape,
 * unchanged). A secondary OpenAI provider is configured under {@code openai}, and
 * {@code chain} defines an ordered failover chain of provider names. An empty
 * chain preserves v1 single-provider behavior.</p>
 *
 * @param mode             which provider to proxy to ({@code live} | {@code demo}, default {@code live})
 * @param chain            ordered failover chain of provider names (empty = single provider)
 * @param name             the primary (Anthropic) provider name
 * @param baseUrl          primary provider base URL (a local stub by default)
 * @param apiKey           primary provider API key (a non-real placeholder by default)
 * @param anthropicVersion value for the {@code anthropic-version} header
 * @param openai           the secondary (OpenAI) provider settings
 */
@ConfigurationProperties("gateway.provider")
public record ProviderProperties(
        @DefaultValue("live") ProviderMode mode,
        List<String> chain,
        @DefaultValue("anthropic") String name,
        @DefaultValue("http://localhost:8089") String baseUrl,
        @DefaultValue("test-provider-key") String apiKey,
        @DefaultValue("2023-06-01") String anthropicVersion,
        @DefaultValue OpenAi openai) {

    /** Defensive-copy compact constructor (absent chain becomes an empty, immutable list). */
    public ProviderProperties {
        chain = chain == null ? List.of() : List.copyOf(chain);
    }

    /**
     * Secondary OpenAI provider settings.
     *
     * @param name    the OpenAI provider name
     * @param baseUrl OpenAI base URL (a local stub by default)
     * @param apiKey  OpenAI API key (a non-real placeholder by default)
     */
    public record OpenAi(
            @DefaultValue("openai") String name,
            @DefaultValue("http://localhost:8090") String baseUrl,
            @DefaultValue("test-openai-key") String apiKey) {
    }
}

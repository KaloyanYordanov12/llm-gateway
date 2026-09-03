package dev.kaloyanyordanov.llmgateway.config;

import dev.kaloyanyordanov.llmgateway.provider.ProviderProperties;
import dev.kaloyanyordanov.llmgateway.proxy.streaming.SseStreamReader;
import dev.kaloyanyordanov.llmgateway.proxy.streaming.StreamingProviderClient;
import java.net.http.HttpClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wiring for streaming: the JDK {@link HttpClient} used to read upstream SSE
 * (HTTP/1.1, same reason as the blocking provider client) and the
 * {@link StreamingProviderClient} bound to the primary provider's settings.
 */
@Configuration
public class StreamingConfig {

    @Bean
    public HttpClient streamingHttpClient() {
        return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    }

    @Bean
    public StreamingProviderClient streamingProviderClient(HttpClient streamingHttpClient,
            SseStreamReader sseStreamReader, ProviderProperties properties, JsonMapper jsonMapper) {
        return new StreamingProviderClient(streamingHttpClient, sseStreamReader, jsonMapper,
                properties.baseUrl(), properties.apiKey(), properties.anthropicVersion());
    }

    /** One virtual thread per streaming task — cheap, so no leaks on slow consumers. */
    @Bean
    public ExecutorService streamingExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}

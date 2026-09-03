package dev.kaloyanyordanov.llmgateway.proxy.streaming;

import dev.kaloyanyordanov.llmgateway.proxy.MessagesRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Opens a streaming call to the (Anthropic-style) provider with the JDK
 * {@link HttpClient}, reading the response body line-by-line and driving the
 * {@link SseStreamReader}. Status is checked before the body is read, so a
 * pre-first-byte failure surfaces as a {@link HttpServerErrorException} (5xx, a
 * failover trigger) or {@link HttpClientErrorException} (4xx) — the caller may
 * fail over only in that window.
 */
public class StreamingProviderClient {

    private static final String MESSAGES_PATH = "/v1/messages";

    private final HttpClient httpClient;
    private final SseStreamReader reader;
    private final ObjectWriter bodyWriter;
    private final String baseUrl;
    private final String apiKey;
    private final String anthropicVersion;

    public StreamingProviderClient(HttpClient httpClient, SseStreamReader reader, JsonMapper jsonMapper,
            String baseUrl, String apiKey, String anthropicVersion) {
        this.httpClient = httpClient;
        this.reader = reader;
        this.bodyWriter = jsonMapper.writer();
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.anthropicVersion = anthropicVersion;
    }

    /**
     * Streams a request, forwarding text deltas and updating the accumulator. On a
     * pre-body failure it throws (before any delta), so the caller may fail over; a
     * failure during the body leaves the accumulator holding the partial result.
     *
     * @param request     the streaming request (its {@code stream} flag should be true)
     * @param accumulator the accumulator to update
     * @param onTextDelta called with each text delta as it arrives
     */
    public void stream(MessagesRequest request, StreamAccumulator accumulator, Consumer<String> onTextDelta) {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + MESSAGES_PATH))
                .header("x-api-key", apiKey)
                .header("anthropic-version", anthropicVersion)
                .header("content-type", "application/json")
                .header("accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(bodyWriter.writeValueAsString(request)))
                .build();

        HttpResponse<Stream<String>> response = send(httpRequest);
        int status = response.statusCode();
        if (status >= 500) {
            drain(response);
            throw new HttpServerErrorException(HttpStatus.valueOf(status));
        }
        if (status >= 400) {
            drain(response);
            throw new HttpClientErrorException(HttpStatus.valueOf(status));
        }
        try (Stream<String> lines = response.body()) {
            reader.read(lines, accumulator, onTextDelta);
        }
    }

    private HttpResponse<Stream<String>> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
        } catch (IOException e) {
            throw new ResourceAccessException("Streaming request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResourceAccessException("Streaming request interrupted");
        }
    }

    private static void drain(HttpResponse<Stream<String>> response) {
        try (Stream<String> body = response.body()) {
            body.forEach(line -> {
                // discard remaining bytes so the connection can be reused
            });
        }
    }
}

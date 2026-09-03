package dev.kaloyanyordanov.llmgateway.proxy.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.kaloyanyordanov.llmgateway.proxy.Message;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesRequest;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class StreamingProviderClientTest {

    private static final MessagesRequest REQUEST =
            new MessagesRequest("m", null, List.of(new Message("user", "hi")), null, null, 10, null, true);

    private final HttpClient httpClient = mock(HttpClient.class);
    private final JsonMapper jsonMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();
    private final StreamingProviderClient client = new StreamingProviderClient(
            httpClient, new SseStreamReader(), jsonMapper, "http://localhost", "key", "2023-06-01");

    @SuppressWarnings("unchecked")
    private static HttpResponse<Stream<String>> response(int status, Stream<String> body) {
        HttpResponse<Stream<String>> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    @Test
    void successReadsBodyIntoResult() throws Exception {
        Stream<String> body = Stream.of(
                "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"hi\"}}",
                "data: {\"type\":\"message_stop\"}");
        doReturn(response(200, body)).when(httpClient).send(any(), any());

        StreamResult result = client.stream(REQUEST, delta -> { });

        assertThat(result.content()).isEqualTo("hi");
        assertThat(result.complete()).isTrue();
    }

    @Test
    void serverErrorThrowsFailoverTrigger() throws Exception {
        doReturn(response(503, Stream.of())).when(httpClient).send(any(), any());

        assertThatThrownBy(() -> client.stream(REQUEST, delta -> { }))
                .isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    void clientErrorThrowsWithoutFailover() throws Exception {
        doReturn(response(400, Stream.of())).when(httpClient).send(any(), any());

        assertThatThrownBy(() -> client.stream(REQUEST, delta -> { }))
                .isInstanceOf(HttpClientErrorException.class);
    }
}

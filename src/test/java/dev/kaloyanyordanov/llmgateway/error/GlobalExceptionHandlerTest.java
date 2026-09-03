package dev.kaloyanyordanov.llmgateway.error;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void circuitOpenMapsToServiceUnavailableEnvelope() {
        CircuitBreaker breaker = CircuitBreaker.ofDefaults("test");
        breaker.transitionToOpenState();
        CallNotPermittedException ex = CallNotPermittedException.createCallNotPermittedException(breaker);

        ResponseEntity<ApiError> response = handler.handleCircuitOpen(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().type()).isEqualTo("error");
        assertThat(response.getBody().error().type()).isEqualTo("overloaded_error");
        assertThat(response.getBody().requestId()).isNotBlank();
    }

    @Test
    void providerFailureMapsToBadGatewayEnvelope() {
        ResponseEntity<ApiError> response =
                handler.handleProviderFailure(new ResourceAccessException("connection refused"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().type()).isEqualTo("api_error");
        assertThat(response.getBody().requestId()).isNotBlank();
    }
}

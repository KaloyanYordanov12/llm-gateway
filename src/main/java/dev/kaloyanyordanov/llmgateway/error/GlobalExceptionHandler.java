package dev.kaloyanyordanov.llmgateway.error;

import dev.kaloyanyordanov.llmgateway.provider.AllProvidersUnavailableException;
import dev.kaloyanyordanov.llmgateway.usage.UnknownModelException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

/**
 * Translates failures into the locked error envelope so clients never see stack
 * traces or leaked internals. Provider transport failures map to {@code 502};
 * an open circuit (provider deemed unhealthy) maps to {@code 503}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String API_ERROR = "api_error";
    private static final String OVERLOADED_ERROR = "overloaded_error";
    private static final String INVALID_REQUEST_ERROR = "invalid_request_error";

    @ExceptionHandler(UnknownModelException.class)
    public ResponseEntity<ApiError> handleUnknownModel(UnknownModelException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrors.of(INVALID_REQUEST_ERROR, ex.getMessage()));
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ApiError> handleCircuitOpen(CallNotPermittedException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiErrors.of(OVERLOADED_ERROR, "Provider temporarily unavailable"));
    }

    @ExceptionHandler(AllProvidersUnavailableException.class)
    public ResponseEntity<ApiError> handleAllProvidersUnavailable(AllProvidersUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiErrors.of(OVERLOADED_ERROR, "All providers are currently unavailable"));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiError> handleProviderFailure(RestClientException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiErrors.of(API_ERROR, "Upstream provider error"));
    }
}

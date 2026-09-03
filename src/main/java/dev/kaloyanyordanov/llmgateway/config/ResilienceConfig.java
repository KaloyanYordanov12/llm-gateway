package dev.kaloyanyordanov.llmgateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.io.IOException;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Resilience wiring for provider calls: a count-based circuit breaker and a retry
 * policy for transient 5xx / I/O failures. Registries are exposed so each provider
 * gets its own named breaker/retry instance.
 */
@Configuration
public class ResilienceConfig {

    static final int SLIDING_WINDOW_SIZE = 10;
    static final int MINIMUM_CALLS = 5;
    static final float FAILURE_RATE_THRESHOLD = 50f;
    static final int HALF_OPEN_PERMITTED_CALLS = 3;
    static final int RETRY_MAX_ATTEMPTS = 3;

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(SLIDING_WINDOW_SIZE)
                .minimumNumberOfCalls(MINIMUM_CALLS)
                .failureRateThreshold(FAILURE_RATE_THRESHOLD)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(HALF_OPEN_PERMITTED_CALLS)
                .automaticTransitionFromOpenToHalfOpenEnabled(false)
                .build();
        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(RETRY_MAX_ATTEMPTS)
                .waitDuration(Duration.ofMillis(100))
                .retryExceptions(
                        HttpServerErrorException.class, ResourceAccessException.class, IOException.class)
                .build();
        return RetryRegistry.of(config);
    }
}

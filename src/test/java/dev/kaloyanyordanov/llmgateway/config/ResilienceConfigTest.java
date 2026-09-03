package dev.kaloyanyordanov.llmgateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import org.junit.jupiter.api.Test;

class ResilienceConfigTest {

    private final ResilienceConfig config = new ResilienceConfig();

    @Test
    void circuitBreakerRegistryUsesConfiguredThresholds() {
        CircuitBreaker breaker = config.circuitBreakerRegistry().circuitBreaker("test");
        CircuitBreakerConfig cbConfig = breaker.getCircuitBreakerConfig();

        assertThat(cbConfig.getFailureRateThreshold()).isEqualTo(ResilienceConfig.FAILURE_RATE_THRESHOLD);
        assertThat(cbConfig.getMinimumNumberOfCalls()).isEqualTo(ResilienceConfig.MINIMUM_CALLS);
        assertThat(cbConfig.getPermittedNumberOfCallsInHalfOpenState())
                .isEqualTo(ResilienceConfig.HALF_OPEN_PERMITTED_CALLS);
    }

    @Test
    void retryRegistryUsesConfiguredMaxAttempts() {
        Retry retry = config.retryRegistry().retry("test");

        assertThat(retry.getRetryConfig().getMaxAttempts()).isEqualTo(ResilienceConfig.RETRY_MAX_ATTEMPTS);
    }
}

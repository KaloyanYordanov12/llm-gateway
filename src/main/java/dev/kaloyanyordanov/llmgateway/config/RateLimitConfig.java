package dev.kaloyanyordanov.llmgateway.config;

import dev.kaloyanyordanov.llmgateway.ratelimit.RateLimiterProperties;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for rate limiting: enables {@link RateLimiterProperties} and provides
 * the shared {@link Clock} the token buckets use (a real UTC clock in production;
 * tests inject a controllable clock directly).
 */
@Configuration
@EnableConfigurationProperties(RateLimiterProperties.class)
public class RateLimitConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

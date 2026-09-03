package dev.kaloyanyordanov.llmgateway.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configurable rate-limit settings. A per-client token bucket holds up to
 * {@code capacity} tokens and refills a full {@code capacity} worth of tokens
 * over {@code refillPeriod} (default: 60 requests per minute).
 *
 * @param capacity     maximum tokens (and burst size) per client
 * @param refillPeriod time to refill a full bucket
 */
@ConfigurationProperties("gateway.rate-limit")
public record RateLimiterProperties(
        @DefaultValue("60") long capacity,
        @DefaultValue("60s") Duration refillPeriod) {
}

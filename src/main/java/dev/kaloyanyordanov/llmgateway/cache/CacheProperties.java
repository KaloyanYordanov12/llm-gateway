package dev.kaloyanyordanov.llmgateway.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Response-cache settings.
 *
 * @param ttl         how long a cached completion stays valid (default 1 hour)
 * @param maximumSize maximum number of cached responses
 */
@ConfigurationProperties("gateway.cache")
public record CacheProperties(
        @DefaultValue("1h") Duration ttl,
        @DefaultValue("10000") long maximumSize) {
}

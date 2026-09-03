package dev.kaloyanyordanov.llmgateway.ratelimit;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

/**
 * Per-client rate limiting. Maintains one {@link TokenBucket} per client id,
 * created lazily from the configured capacity/refill and a shared {@link Clock}.
 */
@Service
public class RateLimiterService {

    private final RateLimiterProperties properties;
    private final Clock clock;
    private final ConcurrentMap<Long, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimiterService(RateLimiterProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Attempts to admit one request for the given client.
     *
     * @param clientId the authenticated client's id
     * @return {@code true} if within the limit; {@code false} if rate-limited
     */
    public boolean tryAcquire(long clientId) {
        TokenBucket bucket = buckets.computeIfAbsent(clientId,
                id -> new TokenBucket(properties.capacity(), properties.refillPeriod(), clock));
        return bucket.tryConsume();
    }
}

package dev.kaloyanyordanov.llmgateway.ratelimit;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
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
    private final AtomicLong rejections = new AtomicLong();

    public RateLimiterService(RateLimiterProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Attempts to admit one request for the given client, counting rejections.
     *
     * @param clientId the authenticated client's id
     * @return {@code true} if within the limit; {@code false} if rate-limited
     */
    public boolean tryAcquire(long clientId) {
        TokenBucket bucket = buckets.computeIfAbsent(clientId,
                id -> new TokenBucket(properties.capacity(), properties.refillPeriod(), clock));
        boolean allowed = bucket.tryConsume();
        if (!allowed) {
            rejections.incrementAndGet();
        }
        return allowed;
    }

    /**
     * @return the total number of requests rejected for exceeding the rate limit
     */
    public long rejectionCount() {
        return rejections.get();
    }
}

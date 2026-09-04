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
     * The bucket capacity is the client's own limit when set, otherwise the
     * configured global default. If a client's limit changes, its bucket is
     * rebuilt at the new capacity on the next request.
     *
     * @param clientId    the authenticated client's id
     * @param clientLimit the client's per-client limit, or {@code null} for the default
     * @return {@code true} if within the limit; {@code false} if rate-limited
     */
    public boolean tryAcquire(long clientId, Integer clientLimit) {
        long capacity = clientLimit != null ? clientLimit : properties.capacity();
        TokenBucket bucket = buckets.compute(clientId, (id, existing) ->
                existing != null && existing.capacity() == capacity
                        ? existing
                        : new TokenBucket(capacity, properties.refillPeriod(), clock));
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

package dev.kaloyanyordanov.llmgateway.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesRequest;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesResponse;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/**
 * In-memory response cache backed by Caffeine. Keys are the canonical hash of the
 * request (see {@link CacheKeyGenerator}); entries expire after the configured
 * TTL. Hit/miss counters are exposed for the dashboard.
 *
 * <p>Because the key includes {@code temperature}, a cache hit on a
 * non-zero-temperature request returns a valid <em>prior</em> completion rather
 * than resampling. This is intentional, documented behaviour — not a bug.</p>
 */
@Service
public class ResponseCacheService {

    private final CacheKeyGenerator keyGenerator;
    private final Cache<String, MessagesResponse> cache;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    public ResponseCacheService(CacheKeyGenerator keyGenerator, CacheProperties properties, Ticker ticker) {
        this.keyGenerator = keyGenerator;
        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.maximumSize())
                .expireAfterWrite(properties.ttl())
                .ticker(ticker)
                .build();
    }

    /**
     * Looks up a cached response, updating hit/miss counters.
     *
     * @param request the request to look up
     * @return the cached response if present and unexpired
     */
    public Optional<MessagesResponse> get(MessagesRequest request) {
        MessagesResponse cached = cache.getIfPresent(keyGenerator.key(request));
        if (cached != null) {
            hits.incrementAndGet();
            return Optional.of(cached);
        }
        misses.incrementAndGet();
        return Optional.empty();
    }

    /**
     * Stores a response under its request's key.
     *
     * @param request  the request
     * @param response the response to cache
     */
    public void put(MessagesRequest request, MessagesResponse response) {
        cache.put(keyGenerator.key(request), response);
    }

    public long hitCount() {
        return hits.get();
    }

    public long missCount() {
        return misses.get();
    }
}

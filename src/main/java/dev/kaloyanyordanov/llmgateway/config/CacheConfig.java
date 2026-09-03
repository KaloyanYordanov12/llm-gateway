package dev.kaloyanyordanov.llmgateway.config;

import com.github.benmanes.caffeine.cache.Ticker;
import dev.kaloyanyordanov.llmgateway.cache.CacheProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cache wiring: enables {@link CacheProperties} and supplies the Caffeine
 * {@link Ticker} (real system ticker in production; tests inject a controllable
 * ticker to drive TTL expiry deterministically).
 */
@Configuration
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig {

    @Bean
    public Ticker cacheTicker() {
        return Ticker.systemTicker();
    }
}

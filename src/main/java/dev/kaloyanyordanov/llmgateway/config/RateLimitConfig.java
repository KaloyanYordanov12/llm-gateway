package dev.kaloyanyordanov.llmgateway.config;

import dev.kaloyanyordanov.llmgateway.ratelimit.RateLimitFilter;
import dev.kaloyanyordanov.llmgateway.ratelimit.RateLimiterProperties;
import dev.kaloyanyordanov.llmgateway.ratelimit.RateLimiterService;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wiring for rate limiting: enables {@link RateLimiterProperties}, provides the
 * shared {@link Clock} the token buckets use (a real UTC clock in production;
 * tests inject a controllable clock directly), and registers the
 * {@link RateLimitFilter} to run after authentication on the proxied API.
 */
@Configuration
@EnableConfigurationProperties(RateLimiterProperties.class)
public class RateLimitConfig {

    /** Filter order: rate limiting runs after authentication. */
    public static final int RATE_LIMIT_FILTER_ORDER = 20;

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
            RateLimiterService rateLimiterService, JsonMapper jsonMapper) {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(new RateLimitFilter(rateLimiterService, jsonMapper));
        registration.addUrlPatterns(AuthConfig.PROXY_PATH_PATTERN);
        registration.setOrder(RATE_LIMIT_FILTER_ORDER);
        return registration;
    }
}

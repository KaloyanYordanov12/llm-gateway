package dev.kaloyanyordanov.llmgateway.config;

import dev.kaloyanyordanov.llmgateway.metrics.GatewayMetrics;
import dev.kaloyanyordanov.llmgateway.metrics.LatencyTracker;
import dev.kaloyanyordanov.llmgateway.metrics.MetricsFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for observability: registers the {@link MetricsFilter} to time proxied
 * requests. It runs after authentication and rate limiting so only admitted
 * requests are measured.
 */
@Configuration
public class MetricsConfig {

    /** Filter order: metrics run after authentication (10) and rate limiting (20). */
    public static final int METRICS_FILTER_ORDER = 30;

    @Bean
    public FilterRegistrationBean<MetricsFilter> metricsFilter(
            GatewayMetrics metrics, LatencyTracker latencyTracker) {
        FilterRegistrationBean<MetricsFilter> registration =
                new FilterRegistrationBean<>(new MetricsFilter(metrics, latencyTracker));
        registration.addUrlPatterns(AuthConfig.PROXY_PATH_PATTERN);
        registration.setOrder(METRICS_FILTER_ORDER);
        return registration;
    }
}

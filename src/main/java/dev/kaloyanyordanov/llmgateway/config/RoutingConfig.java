package dev.kaloyanyordanov.llmgateway.config;

import dev.kaloyanyordanov.llmgateway.provider.routing.RoutingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for per-model routing: enables the {@link RoutingProperties} rule set.
 */
@Configuration
@EnableConfigurationProperties(RoutingProperties.class)
public class RoutingConfig {
}

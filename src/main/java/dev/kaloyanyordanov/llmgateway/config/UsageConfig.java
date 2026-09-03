package dev.kaloyanyordanov.llmgateway.config;

import dev.kaloyanyordanov.llmgateway.usage.PricingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Usage/accounting wiring: enables the {@link PricingProperties} table.
 */
@Configuration
@EnableConfigurationProperties(PricingProperties.class)
public class UsageConfig {
}

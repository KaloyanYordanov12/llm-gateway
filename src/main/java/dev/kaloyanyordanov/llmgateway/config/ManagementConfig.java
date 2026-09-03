package dev.kaloyanyordanov.llmgateway.config;

import dev.kaloyanyordanov.llmgateway.management.AdminAuthFilter;
import dev.kaloyanyordanov.llmgateway.management.ManagementProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wiring for the management API: enables {@link ManagementProperties} and
 * registers the {@link AdminAuthFilter} guarding all {@code /api/*} routes.
 */
@Configuration
@EnableConfigurationProperties(ManagementProperties.class)
public class ManagementConfig {

    /** Path pattern guarded by the admin filter. */
    static final String MANAGEMENT_PATH_PATTERN = "/api/*";

    /** Filter order for admin authentication. */
    public static final int ADMIN_FILTER_ORDER = 10;

    @Bean
    public FilterRegistrationBean<AdminAuthFilter> adminAuthFilter(
            ManagementProperties properties, JsonMapper jsonMapper) {
        FilterRegistrationBean<AdminAuthFilter> registration =
                new FilterRegistrationBean<>(new AdminAuthFilter(properties.key(), jsonMapper));
        registration.addUrlPatterns(MANAGEMENT_PATH_PATTERN);
        registration.setOrder(ADMIN_FILTER_ORDER);
        return registration;
    }
}

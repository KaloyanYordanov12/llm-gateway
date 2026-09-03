package dev.kaloyanyordanov.llmgateway.config;

import dev.kaloyanyordanov.llmgateway.auth.ApiKeyAuthFilter;
import dev.kaloyanyordanov.llmgateway.auth.ClientAuthenticator;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wiring for client authentication: the bcrypt password encoder and the
 * {@link ApiKeyAuthFilter}, registered to guard the proxied {@code /v1/*} API.
 * The management API ({@code /api/*}) and actuator are guarded separately.
 */
@Configuration
public class AuthConfig {

    /** Path pattern protected by the API-key filter. */
    static final String PROXY_PATH_PATTERN = "/v1/*";

    /** Filter order: authentication runs before rate limiting. */
    public static final int AUTH_FILTER_ORDER = 10;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilter(
            ClientAuthenticator authenticator, JsonMapper jsonMapper) {
        FilterRegistrationBean<ApiKeyAuthFilter> registration =
                new FilterRegistrationBean<>(new ApiKeyAuthFilter(authenticator, jsonMapper));
        registration.addUrlPatterns(PROXY_PATH_PATTERN);
        registration.setOrder(AUTH_FILTER_ORDER);
        return registration;
    }
}

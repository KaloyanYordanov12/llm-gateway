package dev.kaloyanyordanov.llmgateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.kaloyanyordanov.llmgateway.ratelimit.RateLimitFilter;
import dev.kaloyanyordanov.llmgateway.ratelimit.RateLimiterService;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import tools.jackson.databind.json.JsonMapper;

class RateLimitConfigTest {

    private final RateLimitConfig config = new RateLimitConfig();

    @Test
    void clockIsSystemUtc() {
        assertThat(config.clock()).isNotNull();
        assertThat(config.clock().getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void filterRegistrationGuardsProxyPathAndRunsAfterAuth() {
        FilterRegistrationBean<RateLimitFilter> registration =
                config.rateLimitFilter(mock(RateLimiterService.class), JsonMapper.builder().build());

        assertThat(registration.getFilter()).isInstanceOf(RateLimitFilter.class);
        assertThat(registration.getUrlPatterns()).containsExactly("/v1/*");
        assertThat(registration.getOrder()).isEqualTo(RateLimitConfig.RATE_LIMIT_FILTER_ORDER);
        assertThat(RateLimitConfig.RATE_LIMIT_FILTER_ORDER)
                .isGreaterThan(AuthConfig.AUTH_FILTER_ORDER);
    }
}

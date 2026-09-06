package dev.kaloyanyordanov.llmgateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.llmgateway.management.AdminAuthFilter;
import dev.kaloyanyordanov.llmgateway.management.ManagementProperties;
import dev.kaloyanyordanov.llmgateway.provider.ProviderMode;
import dev.kaloyanyordanov.llmgateway.provider.ProviderProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import tools.jackson.databind.json.JsonMapper;

class ManagementConfigTest {

    private final ManagementConfig config = new ManagementConfig();

    private static ProviderProperties providerProperties(ProviderMode mode) {
        return new ProviderProperties(mode, List.of(), "anthropic", "http://localhost:8089",
                "test-provider-key", "2023-06-01",
                new ProviderProperties.OpenAi("openai", "http://localhost:8090", "test-openai-key"));
    }

    @Test
    void filterRegistrationGuardsManagementPath() {
        FilterRegistrationBean<AdminAuthFilter> registration = config.adminAuthFilter(
                new ManagementProperties("k"), providerProperties(ProviderMode.LIVE),
                JsonMapper.builder().build());

        assertThat(registration.getFilter()).isInstanceOf(AdminAuthFilter.class);
        assertThat(registration.getUrlPatterns()).containsExactly("/api/*");
        assertThat(registration.getOrder()).isEqualTo(ManagementConfig.ADMIN_FILTER_ORDER);
    }

    @Test
    void filterRegistrationBuildsInEitherMode() {
        // Demo mode wires the same guarded registration; the read exemption lives
        // inside the filter, not in the URL patterns it is registered on.
        FilterRegistrationBean<AdminAuthFilter> registration = config.adminAuthFilter(
                new ManagementProperties("k"), providerProperties(ProviderMode.DEMO),
                JsonMapper.builder().build());

        assertThat(registration.getFilter()).isInstanceOf(AdminAuthFilter.class);
        assertThat(registration.getUrlPatterns()).containsExactly("/api/*");
    }
}

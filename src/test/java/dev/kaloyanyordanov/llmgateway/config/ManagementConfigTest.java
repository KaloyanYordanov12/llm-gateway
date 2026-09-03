package dev.kaloyanyordanov.llmgateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.llmgateway.management.AdminAuthFilter;
import dev.kaloyanyordanov.llmgateway.management.ManagementProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import tools.jackson.databind.json.JsonMapper;

class ManagementConfigTest {

    private final ManagementConfig config = new ManagementConfig();

    @Test
    void filterRegistrationGuardsManagementPath() {
        FilterRegistrationBean<AdminAuthFilter> registration =
                config.adminAuthFilter(new ManagementProperties("k"), JsonMapper.builder().build());

        assertThat(registration.getFilter()).isInstanceOf(AdminAuthFilter.class);
        assertThat(registration.getUrlPatterns()).containsExactly("/api/*");
        assertThat(registration.getOrder()).isEqualTo(ManagementConfig.ADMIN_FILTER_ORDER);
    }
}

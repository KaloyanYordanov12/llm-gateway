package dev.kaloyanyordanov.llmgateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.kaloyanyordanov.llmgateway.auth.ApiKeyAuthFilter;
import dev.kaloyanyordanov.llmgateway.auth.ClientAuthenticator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

class AuthConfigTest {

    private final AuthConfig config = new AuthConfig();

    @Test
    void passwordEncoderIsFunctionalBcrypt() {
        PasswordEncoder encoder = config.passwordEncoder();

        assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);
        String hash = encoder.encode("secret");
        assertThat(encoder.matches("secret", hash)).isTrue();
        assertThat(encoder.matches("wrong", hash)).isFalse();
    }

    @Test
    void filterRegistrationGuardsProxyPathWithApiKeyFilter() {
        FilterRegistrationBean<ApiKeyAuthFilter> registration =
                config.apiKeyAuthFilter(mock(ClientAuthenticator.class), JsonMapper.builder().build());

        assertThat(registration).isNotNull();
        assertThat(registration.getFilter()).isInstanceOf(ApiKeyAuthFilter.class);
        assertThat(registration.getUrlPatterns()).containsExactly("/v1/*");
    }
}

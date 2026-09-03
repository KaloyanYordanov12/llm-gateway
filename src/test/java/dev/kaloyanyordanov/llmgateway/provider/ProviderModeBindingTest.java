package dev.kaloyanyordanov.llmgateway.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Verifies the fail-secure binding of {@code gateway.provider.mode}: a valid value
 * binds, and an unrecognized value fails startup rather than silently defaulting.
 */
class ProviderModeBindingTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(PropsConfig.class);

    @Test
    void unrecognizedModeFailsStartup() {
        runner.withPropertyValues("gateway.provider.mode=bogus")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void demoModeBindsSuccessfully() {
        runner.withPropertyValues("gateway.provider.mode=demo")
                .run(context -> assertThat(context).hasNotFailed()
                        .getBean(ProviderProperties.class)
                        .extracting(ProviderProperties::mode)
                        .isEqualTo(ProviderMode.DEMO));
    }

    @Test
    void defaultModeIsLiveWhenUnset() {
        runner.run(context -> assertThat(context).hasNotFailed()
                .getBean(ProviderProperties.class)
                .extracting(ProviderProperties::mode)
                .isEqualTo(ProviderMode.LIVE));
    }

    @EnableConfigurationProperties(ProviderProperties.class)
    static class PropsConfig {
    }
}

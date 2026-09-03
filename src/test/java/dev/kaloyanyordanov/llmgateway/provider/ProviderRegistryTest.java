package dev.kaloyanyordanov.llmgateway.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderRegistryTest {

    private static ProviderClient provider(String name) {
        ProviderClient provider = mock(ProviderClient.class);
        when(provider.name()).thenReturn(name);
        return provider;
    }

    @Test
    void getReturnsTheNamedProvider() {
        ProviderClient anthropic = provider("anthropic");
        ProviderClient other = provider("other");
        ProviderRegistry registry = new ProviderRegistry(List.of(anthropic, other), "anthropic");

        assertThat(registry.get("other")).isSameAs(other);
    }

    @Test
    void getDefaultReturnsConfiguredDefault() {
        ProviderClient anthropic = provider("anthropic");
        ProviderClient other = provider("other");
        ProviderRegistry registry = new ProviderRegistry(List.of(anthropic, other), "anthropic");

        assertThat(registry.getDefault()).isSameAs(anthropic);
    }

    @Test
    void getUnknownProviderThrows() {
        ProviderRegistry registry = new ProviderRegistry(List.of(provider("anthropic")), "anthropic");

        assertThatThrownBy(() -> registry.get("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void namesListsAllRegisteredProviders() {
        ProviderRegistry registry =
                new ProviderRegistry(List.of(provider("anthropic"), provider("other")), "anthropic");

        assertThat(registry.names()).containsExactlyInAnyOrder("anthropic", "other");
    }
}

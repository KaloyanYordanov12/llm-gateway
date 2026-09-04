package dev.kaloyanyordanov.llmgateway.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ClientTest {

    @Test
    void gettersReflectConstructor() {
        Client client = new Client("acme", "hash", true);

        assertThat(client.getId()).isNull();
        assertThat(client.getName()).isEqualTo("acme");
        assertThat(client.getApiKeyHash()).isEqualTo("hash");
        assertThat(client.isEnabled()).isTrue();
    }

    @Test
    void defaultConstructorLeavesLimitAndBudgetUnset() {
        Client client = new Client("acme", "hash", true);

        // Unset => global default limit, no spend cap (backward-compatible).
        assertThat(client.getRateLimit()).isNull();
        assertThat(client.getBudget()).isNull();
    }

    @Test
    void fullConstructorCarriesLimitAndBudget() {
        Client client = new Client("acme", "hash", true, 120, new BigDecimal("5.00"));

        assertThat(client.getRateLimit()).isEqualTo(120);
        assertThat(client.getBudget()).isEqualByComparingTo("5.00");
    }

    @Test
    void settersUpdateMultiTenantControls() {
        Client client = new Client("acme", "hash", true);

        client.setRateLimit(30);
        client.setBudget(new BigDecimal("1.25"));
        client.setEnabled(false);

        assertThat(client.getRateLimit()).isEqualTo(30);
        assertThat(client.getBudget()).isEqualByComparingTo("1.25");
        assertThat(client.isEnabled()).isFalse();
    }

    @Test
    void limitAndBudgetCanBeClearedBackToDefaults() {
        Client client = new Client("acme", "hash", true, 10, new BigDecimal("2.00"));

        client.setRateLimit(null);
        client.setBudget(null);

        assertThat(client.getRateLimit()).isNull();
        assertThat(client.getBudget()).isNull();
    }

    @Test
    void enabledFlagReflectsConstructor() {
        assertThat(new Client("a", "h", false).isEnabled()).isFalse();
        assertThat(new Client("a", "h", true).isEnabled()).isTrue();
    }

    @Test
    void prePersistSetsCreatedAndUpdatedToSameInstant() {
        Client client = new Client("acme", "hash", true);

        client.onCreate();

        assertThat(client.getCreatedAt()).isNotNull();
        assertThat(client.getUpdatedAt()).isNotNull();
        assertThat(client.getUpdatedAt()).isEqualTo(client.getCreatedAt());
    }

    @Test
    void preUpdateLeavesCreatedAtUntouched() {
        Client client = new Client("acme", "hash", true);
        client.onCreate();
        Instant created = client.getCreatedAt();

        client.onUpdate();

        assertThat(client.getCreatedAt()).isEqualTo(created);
        assertThat(client.getUpdatedAt()).isNotNull();
    }
}

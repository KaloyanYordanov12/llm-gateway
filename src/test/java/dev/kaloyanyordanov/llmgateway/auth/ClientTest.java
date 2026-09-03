package dev.kaloyanyordanov.llmgateway.auth;

import static org.assertj.core.api.Assertions.assertThat;

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

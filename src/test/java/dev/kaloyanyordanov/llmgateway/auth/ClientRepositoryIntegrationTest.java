package dev.kaloyanyordanov.llmgateway.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.llmgateway.AbstractPostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ClientRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private ClientRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void savesAndPopulatesTimestamps() {
        Client saved = repository.save(new Client("acme", "hash", true));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByEnabledTrueExcludesDisabledClients() {
        repository.save(new Client("enabled-1", "h1", true));
        repository.save(new Client("enabled-2", "h2", true));
        repository.save(new Client("disabled-1", "h3", false));

        List<Client> enabled = repository.findByEnabledTrue();

        assertThat(enabled).extracting(Client::getName)
                .containsExactlyInAnyOrder("enabled-1", "enabled-2");
    }

    @Test
    void findByNameReturnsMatch() {
        repository.save(new Client("acme", "hash", true));

        assertThat(repository.findByName("acme")).isPresent();
        assertThat(repository.findByName("missing")).isEmpty();
    }
}

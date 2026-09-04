package dev.kaloyanyordanov.llmgateway.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.kaloyanyordanov.llmgateway.auth.Client;
import dev.kaloyanyordanov.llmgateway.auth.ClientRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class ClientManagementServiceTest {

    private final ClientRepository repository = mock(ClientRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final ClientManagementService service = new ClientManagementService(repository, encoder);

    @Test
    void createGeneratesAKeyStoresOnlyItsHashAndReturnsRawKeyOnce() {
        when(repository.findByName("acme")).thenReturn(Optional.empty());
        when(repository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        CreatedClientView created = service.create("acme", 30, new BigDecimal("2.00"));

        ArgumentCaptor<Client> saved = ArgumentCaptor.forClass(Client.class);
        org.mockito.Mockito.verify(repository).save(saved.capture());
        // The stored value is a bcrypt hash, never the raw key.
        assertThat(saved.getValue().getApiKeyHash()).isNotEqualTo(created.apiKey());
        assertThat(encoder.matches(created.apiKey(), saved.getValue().getApiKeyHash())).isTrue();
        // The raw key is a recognisable prefixed token.
        assertThat(created.apiKey()).startsWith("sk-gw-");
        assertThat(created.rateLimit()).isEqualTo(30);
        assertThat(created.budget()).isEqualByComparingTo("2.00");
        assertThat(created.enabled()).isTrue();
    }

    @Test
    void createGeneratesADistinctKeyEachTime() {
        when(repository.findByName(any())).thenReturn(Optional.empty());
        when(repository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        String first = service.create("a", null, null).apiKey();
        String second = service.create("b", null, null).apiKey();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void createRejectsABlankName() {
        assertThatThrownBy(() -> service.create("  ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsADuplicateName() {
        when(repository.findByName("acme"))
                .thenReturn(Optional.of(new Client("acme", "hash", true)));

        assertThatThrownBy(() -> service.create("acme", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("acme");
    }

    @Test
    void updateAppliesOnlyThePresentFields() {
        Client existing = new Client("acme", "hash", true, 30, new BigDecimal("2.00"));
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(repository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        // Only the budget is provided; rate limit and enabled must be untouched.
        Client updated = service.update(5L, null, new BigDecimal("9.00"), null);

        assertThat(updated.getBudget()).isEqualByComparingTo("9.00");
        assertThat(updated.getRateLimit()).isEqualTo(30);
        assertThat(updated.isEnabled()).isTrue();
    }

    @Test
    void updateCanDisableAClientAndChangeItsLimit() {
        Client existing = new Client("acme", "hash", true, 30, new BigDecimal("2.00"));
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(repository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        Client updated = service.update(5L, 10, null, false);

        assertThat(updated.getRateLimit()).isEqualTo(10);
        assertThat(updated.isEnabled()).isFalse();
        assertThat(updated.getBudget()).isEqualByComparingTo("2.00");
    }

    @Test
    void updateRejectsAnUnknownId() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, 1, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

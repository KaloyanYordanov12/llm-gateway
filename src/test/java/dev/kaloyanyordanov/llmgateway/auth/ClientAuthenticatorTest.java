package dev.kaloyanyordanov.llmgateway.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class ClientAuthenticatorTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final ClientRepository repository = Mockito.mock(ClientRepository.class);
    private final ClientAuthenticator authenticator = new ClientAuthenticator(repository, encoder);

    @Test
    void validKeyResolvesEnabledClient() {
        Client client = new Client("acme", encoder.encode("secret"), true);
        when(repository.findByEnabledTrue()).thenReturn(List.of(client));

        assertThat(authenticator.authenticate("secret")).containsSame(client);
    }

    @Test
    void invalidKeyReturnsEmpty() {
        Client client = new Client("acme", encoder.encode("secret"), true);
        when(repository.findByEnabledTrue()).thenReturn(List.of(client));

        assertThat(authenticator.authenticate("wrong")).isEmpty();
    }

    @Test
    void nullKeyReturnsEmptyWithoutRepositoryLookup() {
        assertThat(authenticator.authenticate(null)).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void blankKeyReturnsEmptyWithoutRepositoryLookup() {
        assertThat(authenticator.authenticate("   ")).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void picksMatchingClientAmongSeveral() {
        Client a = new Client("a", encoder.encode("k1"), true);
        Client b = new Client("b", encoder.encode("k2"), true);
        when(repository.findByEnabledTrue()).thenReturn(List.of(a, b));

        assertThat(authenticator.authenticate("k2")).containsSame(b);
    }
}

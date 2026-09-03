package dev.kaloyanyordanov.llmgateway.auth;

import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Resolves a presented {@code x-api-key} to an enabled {@link Client} by
 * bcrypt-matching it against the stored hash of each enabled client. Disabled
 * clients are never considered (they are excluded at the repository level).
 */
@Component
public class ClientAuthenticator {

    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;

    public ClientAuthenticator(ClientRepository clientRepository, PasswordEncoder passwordEncoder) {
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Authenticates a presented API key.
     *
     * @param presentedKey the raw key from the {@code x-api-key} header (may be null)
     * @return the matching enabled client, or empty if the key is missing, blank,
     *     or does not match any enabled client
     */
    public Optional<Client> authenticate(String presentedKey) {
        if (presentedKey == null || presentedKey.isBlank()) {
            return Optional.empty();
        }
        for (Client client : clientRepository.findByEnabledTrue()) {
            if (passwordEncoder.matches(presentedKey, client.getApiKeyHash())) {
                return Optional.of(client);
            }
        }
        return Optional.empty();
    }
}

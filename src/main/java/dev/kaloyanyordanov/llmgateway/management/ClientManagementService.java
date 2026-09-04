package dev.kaloyanyordanov.llmgateway.management;

import dev.kaloyanyordanov.llmgateway.auth.Client;
import dev.kaloyanyordanov.llmgateway.auth.ClientRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Admin-side client lifecycle: creating clients (generating a fresh API key and
 * persisting only its bcrypt hash) and updating a client's multi-tenant controls.
 * The raw key is returned to the caller exactly once, at creation, and is never
 * stored or logged.
 */
@Service
public class ClientManagementService {

    /** Prefix for generated keys; makes a leaked key easy to recognise and revoke. */
    private static final String KEY_PREFIX = "sk-gw-";
    private static final int KEY_BYTES = 32;

    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder keyEncoder = Base64.getUrlEncoder().withoutPadding();

    public ClientManagementService(ClientRepository clientRepository, PasswordEncoder passwordEncoder) {
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates a client with a freshly generated API key.
     *
     * @param name      client name (must be non-blank and unique)
     * @param rateLimit optional per-client rate limit ({@code null} for the default)
     * @param budget    optional hard spend cap ({@code null} for no cap)
     * @return a view of the created client including its raw key (revealed only here)
     * @throws IllegalArgumentException if the name is blank or already in use
     */
    public CreatedClientView create(String name, Integer rateLimit, BigDecimal budget) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Client name must not be blank");
        }
        if (clientRepository.findByName(name).isPresent()) {
            throw new IllegalArgumentException("Client name already in use: " + name);
        }
        String rawKey = generateKey();
        Client client = new Client(name, passwordEncoder.encode(rawKey), true, rateLimit, budget);
        return CreatedClientView.of(clientRepository.save(client), rawKey);
    }

    /**
     * Updates a client's multi-tenant controls. Each argument is applied only when
     * present; a {@code null} leaves that field unchanged.
     *
     * @param id        the client id
     * @param rateLimit new rate limit, or {@code null} to leave unchanged
     * @param budget    new budget cap, or {@code null} to leave unchanged
     * @param enabled   new enabled flag, or {@code null} to leave unchanged
     * @return the updated client
     * @throws IllegalArgumentException if no client has the given id
     */
    public Client update(long id, Integer rateLimit, BigDecimal budget, Boolean enabled) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No client with id " + id));
        if (rateLimit != null) {
            client.setRateLimit(rateLimit);
        }
        if (budget != null) {
            client.setBudget(budget);
        }
        if (enabled != null) {
            client.setEnabled(enabled);
        }
        return clientRepository.save(client);
    }

    private String generateKey() {
        byte[] bytes = new byte[KEY_BYTES];
        random.nextBytes(bytes);
        return KEY_PREFIX + keyEncoder.encodeToString(bytes);
    }
}

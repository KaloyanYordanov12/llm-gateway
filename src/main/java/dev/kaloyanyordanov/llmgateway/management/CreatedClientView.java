package dev.kaloyanyordanov.llmgateway.management;

import dev.kaloyanyordanov.llmgateway.auth.Client;
import java.math.BigDecimal;

/**
 * Response returned once when a client is created. Unlike {@link ClientView}, this
 * carries the raw {@code apiKey} — it is shown to the admin exactly here and is
 * never persisted, logged, or retrievable again.
 *
 * @param id        client id
 * @param name      client name
 * @param enabled   whether the client may authenticate
 * @param rateLimit per-client rate limit, or {@code null} for the global default
 * @param budget    hard spend cap, or {@code null} for no cap
 * @param apiKey    the raw API key, shown only at creation
 */
public record CreatedClientView(
        Long id, String name, boolean enabled, Integer rateLimit, BigDecimal budget, String apiKey) {

    /**
     * @param client the persisted client
     * @param rawKey the raw API key to reveal once
     * @return a creation view including the raw key
     */
    public static CreatedClientView of(Client client, String rawKey) {
        return new CreatedClientView(client.getId(), client.getName(), client.isEnabled(),
                client.getRateLimit(), client.getBudget(), rawKey);
    }
}

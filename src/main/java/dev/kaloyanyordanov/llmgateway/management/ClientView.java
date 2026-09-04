package dev.kaloyanyordanov.llmgateway.management;

import dev.kaloyanyordanov.llmgateway.auth.Client;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read-only view of a client for the management API. Deliberately omits the
 * bcrypt key hash — secrets are never exposed over the API. Includes the
 * multi-tenant controls (rate limit and budget) so the dashboard can show
 * per-client configuration.
 *
 * @param id        client id
 * @param name      client name
 * @param enabled   whether the client may authenticate
 * @param rateLimit per-client rate limit, or {@code null} for the global default
 * @param budget    hard spend cap, or {@code null} for no cap
 * @param createdAt when the client was created
 */
public record ClientView(
        Long id, String name, boolean enabled, Integer rateLimit, BigDecimal budget, Instant createdAt) {

    /**
     * @param client the entity to project
     * @return a view of the client without its key hash
     */
    public static ClientView from(Client client) {
        return new ClientView(client.getId(), client.getName(), client.isEnabled(),
                client.getRateLimit(), client.getBudget(), client.getCreatedAt());
    }
}

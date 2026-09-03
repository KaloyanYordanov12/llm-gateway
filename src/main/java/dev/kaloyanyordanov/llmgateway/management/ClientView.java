package dev.kaloyanyordanov.llmgateway.management;

import dev.kaloyanyordanov.llmgateway.auth.Client;
import java.time.Instant;

/**
 * Read-only view of a client for the management API. Deliberately omits the
 * bcrypt key hash — secrets are never exposed over the API.
 *
 * @param id        client id
 * @param name      client name
 * @param enabled   whether the client may authenticate
 * @param createdAt when the client was created
 */
public record ClientView(Long id, String name, boolean enabled, Instant createdAt) {

    /**
     * @param client the entity to project
     * @return a view of the client without its key hash
     */
    public static ClientView from(Client client) {
        return new ClientView(client.getId(), client.getName(), client.isEnabled(), client.getCreatedAt());
    }
}

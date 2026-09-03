package dev.kaloyanyordanov.llmgateway.auth;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link Client} records.
 */
public interface ClientRepository extends JpaRepository<Client, Long> {

    /**
     * Returns all enabled clients. Authentication verifies a presented key against
     * the bcrypt hash of each enabled client.
     *
     * @return enabled clients
     */
    List<Client> findByEnabledTrue();

    /**
     * Finds a client by its unique name.
     *
     * @param name the client name
     * @return the client, if present
     */
    Optional<Client> findByName(String name);
}

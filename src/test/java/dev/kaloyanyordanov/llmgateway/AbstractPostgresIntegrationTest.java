package dev.kaloyanyordanov.llmgateway;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for full-context integration tests. Boots a real Postgres via
 * Testcontainers and wires it in with {@code @ServiceConnection}, so Flyway runs
 * the real migrations and JPA talks to a real database. The container is static
 * and shared across all subclasses in the JVM.
 */
@SpringBootTest
@Testcontainers
public abstract class AbstractPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.5");
}

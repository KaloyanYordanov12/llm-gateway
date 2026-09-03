package dev.kaloyanyordanov.llmgateway;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for full-context integration tests. Uses the Testcontainers
 * singleton pattern: one Postgres container is started once and reused across
 * every subclass in the JVM (Ryuk tears it down at JVM exit). This avoids the
 * per-class start/stop lifecycle of {@code @Testcontainers}, which would stop a
 * shared static container after the first test class and break the rest.
 *
 * <p>Flyway runs the real migrations against this container and JPA talks to a
 * real database, exactly as in production.</p>
 */
@SpringBootTest
public abstract class AbstractPostgresIntegrationTest {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.5");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * The container is shared across all test classes, so each test starts from a
     * clean slate. TRUNCATE ... CASCADE clears data (respecting the usage → clients
     * FK) without touching Flyway's schema history.
     */
    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE usage_records, clients RESTART IDENTITY CASCADE");
    }
}

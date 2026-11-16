package br.com.balancecontrol;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests against a real PostgreSQL 16.
 *
 * <p>Singleton container: started once per JVM and shared by every subclass, so the
 * Spring context cache is reused and the suite does not pay for one container per class.
 * Ryuk stops it when the JVM exits.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected JdbcTemplate jdbc;

    /** Every test starts from empty domain tables (the schema itself comes from Flyway). */
    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE order_item, orders, item_balance_adjustment, item, agency RESTART IDENTITY CASCADE");
    }

    protected long insertAgency(String code) {
        return jdbc.queryForObject("INSERT INTO agency (code, acronym, name, state) VALUES (?, ?, ?, 'DF') RETURNING id",
                Long.class, code, code, "Agency " + code);
    }

    protected long insertItem(String code, int balance) {
        return insertItem(code, balance, true);
    }

    protected long insertItem(String code, int balance, boolean active) {
        return jdbc.queryForObject("INSERT INTO item (code, description, unit, balance, active) VALUES (?, ?, 'UN', ?, ?) RETURNING id",
                Long.class, code, "Item " + code, balance, active);
    }

    protected int balanceOf(long itemId) {
        return jdbc.queryForObject("SELECT balance FROM item WHERE id = ?", Integer.class, itemId);
    }

    protected int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}

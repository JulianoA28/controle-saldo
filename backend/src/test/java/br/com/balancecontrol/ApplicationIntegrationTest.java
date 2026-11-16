package br.com.balancecontrol;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ApplicationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate http;

    @Test
    void allFlywayMigrationsSucceeded() {
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE NOT success",
                Integer.class);
        assertThat(applied).isZero();
    }

    @Test
    @SuppressWarnings("rawtypes")
    void healthIsUp() {
        ResponseEntity<Map> response = http.getForEntity("/actuator/health", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }
}

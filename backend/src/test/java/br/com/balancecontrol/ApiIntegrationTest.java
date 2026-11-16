package br.com.balancecontrol;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** HTTP contract: status codes and the standard error body, end to end. */
@SuppressWarnings({"rawtypes", "unchecked"})
class ApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate http;

    @Test
    void placingAnOrderReturns201ThenResendReturns200WithSameOrder() {
        insertAgency("ORG-1");
        insertItem("PEN", 10);
        Map<String, Object> body = Map.of("externalId", UUID.randomUUID().toString(), "agencyCode", "ORG-1",
                "requester", "integration", "origin", "INTEGRATION",
                "items", List.of(Map.of("itemCode", "PEN", "quantity", 2)));

        ResponseEntity<Map> first = http.postForEntity("/orders", body, Map.class);
        ResponseEntity<Map> resend = http.postForEntity("/orders", body, Map.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getHeaders().getLocation()).hasPath("/orders/" + first.getBody().get("id"));
        assertThat(resend.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resend.getBody().get("id")).isEqualTo(first.getBody().get("id"));
    }

    @Test
    void businessErrorsUseTheStandardBody() {
        insertAgency("ORG-1");
        insertItem("PEN", 1);
        Map<String, Object> body = Map.of("externalId", UUID.randomUUID().toString(), "agencyCode", "ORG-1",
                "requester", "user", "origin", "PORTAL", "items", List.of(Map.of("itemCode", "PEN", "quantity", 5)));

        ResponseEntity<Map> response = http.postForEntity("/orders", body, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).containsEntry("code", "INSUFFICIENT_BALANCE");
        assertThat((List<Map>) response.getBody().get("details"))
                .singleElement().satisfies(d -> assertThat(d).containsEntry("available", 1));
    }

    @Test
    void invalidInputIs400AndUnknownRouteIs404WithStandardBody() {
        ResponseEntity<Map> invalid = http.postForEntity("/orders", Map.of("items", List.of()), Map.class);
        ResponseEntity<Map> unknown = http.getForEntity("/nothing-here", Map.class);

        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody()).containsEntry("code", "INVALID_DATA");
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getBody()).containsEntry("code", "ROUTE_NOT_FOUND");
    }
}

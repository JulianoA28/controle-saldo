package br.com.balancecontrol.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.balancecontrol.AbstractIntegrationTest;
import br.com.balancecontrol.common.error.BusinessException;
import br.com.balancecontrol.order.dto.OrderLineRequest;
import br.com.balancecontrol.order.dto.PlaceOrderRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OrderServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    OrderService service;

    long pen;
    long tape;

    @BeforeEach
    void data() {
        insertAgency("ORG-1");
        pen = insertItem("PEN", 10);
        tape = insertItem("TAPE", 5);
    }

    @Test
    void placingDebitsEveryLine() {
        PlaceOrderResult result = service.place(order(UUID.randomUUID(), line("PEN", 3), line("TAPE", 5)));

        assertThat(result.created()).isTrue();
        assertThat(result.order().status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.order().items()).hasSize(2);
        assertThat(balanceOf(pen)).isEqualTo(7);
        assertThat(balanceOf(tape)).isZero();
    }

    @Test
    void orderIsAllOrNothing() {
        assertThatThrownBy(() -> service.place(order(UUID.randomUUID(), line("PEN", 3), line("TAPE", 6))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INSUFFICIENT_BALANCE")
                .satisfies(e -> assertThat(((BusinessException) e).getDetails()).hasSize(1));

        assertThat(balanceOf(pen)).as("PEN debit rolled back").isEqualTo(10);
        assertThat(balanceOf(tape)).isEqualTo(5);
        assertThat(count("SELECT count(*) FROM orders")).isZero();
    }

    @Test
    void resendWithSameIdReturnsTheSameOrderWithoutDebitingAgain() {
        UUID id = UUID.randomUUID();
        PlaceOrderResult first = service.place(order(id, line("PEN", 2)));
        PlaceOrderResult again = service.place(order(id, line("PEN", 2)));

        assertThat(again.created()).isFalse();
        assertThat(again.order().id()).isEqualTo(first.order().id());
        assertThat(balanceOf(pen)).isEqualTo(8);
    }

    @Test
    void reusingAnIdForDifferentContentIsRejected() {
        UUID id = UUID.randomUUID();
        service.place(order(id, line("PEN", 2)));

        assertThatThrownBy(() -> service.place(order(id, line("PEN", 3))))
                .hasFieldOrPropertyWithValue("code", "IDEMPOTENCY_KEY_REUSED");
        assertThat(balanceOf(pen)).isEqualTo(8);
    }

    @Test
    void rejectsUnknownAgencyUnknownItemInactiveItemAndDuplicatedItem() {
        insertItem("OLD", 10, false);

        assertThatThrownBy(() -> service.place(new PlaceOrderRequest(UUID.randomUUID(), "NOPE", "user",
                OrderOrigin.PORTAL, List.of(line("PEN", 1))))).hasFieldOrPropertyWithValue("code", "AGENCY_NOT_FOUND");
        assertThatThrownBy(() -> service.place(order(UUID.randomUUID(), line("GHOST", 1))))
                .hasFieldOrPropertyWithValue("code", "ITEM_NOT_FOUND");
        assertThatThrownBy(() -> service.place(order(UUID.randomUUID(), line("OLD", 1))))
                .hasFieldOrPropertyWithValue("code", "ITEM_INACTIVE");
        assertThatThrownBy(() -> service.place(order(UUID.randomUUID(), line("PEN", 1), line("PEN", 2))))
                .hasFieldOrPropertyWithValue("code", "DUPLICATE_ITEM");
        assertThat(balanceOf(pen)).isEqualTo(10);
    }

    @Test
    void cancellingReturnsBalanceAndCanHappenOnlyOnce() {
        long orderId = service.place(order(UUID.randomUUID(), line("PEN", 4), line("TAPE", 1))).order().id();

        service.cancel(orderId, "Wrong agency");

        assertThat(balanceOf(pen)).isEqualTo(10);
        assertThat(balanceOf(tape)).isEqualTo(5);
        assertThat(service.get(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(service.get(orderId).cancellationReason()).isEqualTo("Wrong agency");
        assertThatThrownBy(() -> service.cancel(orderId, null))
                .hasFieldOrPropertyWithValue("code", "ORDER_ALREADY_CANCELLED");
        assertThat(balanceOf(pen)).isEqualTo(10);
        assertThatThrownBy(() -> service.cancel(999_999, null))
                .hasFieldOrPropertyWithValue("code", "ORDER_NOT_FOUND");
    }

    static PlaceOrderRequest order(UUID id, OrderLineRequest... lines) {
        return new PlaceOrderRequest(id, "ORG-1", "requester", OrderOrigin.INTEGRATION, List.of(lines));
    }

    static OrderLineRequest line(String code, int quantity) {
        return new OrderLineRequest(code, quantity);
    }
}

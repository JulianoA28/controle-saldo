package br.com.balancecontrol.order;

import static br.com.balancecontrol.order.OrderServiceIntegrationTest.line;
import static br.com.balancecontrol.order.OrderServiceIntegrationTest.order;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.balancecontrol.AbstractIntegrationTest;
import br.com.balancecontrol.Concurrently;
import br.com.balancecontrol.Concurrently.Outcome;
import br.com.balancecontrol.common.error.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** R4/R3/R6 under real contention: threads released together against a real PostgreSQL. */
class OrderConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    OrderService service;

    @BeforeEach
    void agency() {
        insertAgency("ORG-1");
    }

    @Test
    void concurrentOrdersNeverOversell() throws Exception {
        long pen = insertItem("PEN", 50);

        List<Outcome<PlaceOrderResult>> outcomes = Concurrently.run(Concurrently.times(100,
                () -> service.place(order(UUID.randomUUID(), line("PEN", 1)))));

        assertThat(outcomes.stream().filter(Outcome::ok)).hasSize(50);
        assertThat(outcomes.stream().filter(o -> !o.ok()))
                .allSatisfy(o -> assertThat(o.error()).hasFieldOrPropertyWithValue("code", "INSUFFICIENT_BALANCE"));
        assertThat(balanceOf(pen)).isZero();
        assertThat(count("SELECT coalesce(sum(quantity), 0) FROM order_item WHERE item_id = ?", pen)).isEqualTo(50);
    }

    @Test
    void ordersWithSameItemsInOppositeOrderDoNotDeadlock() throws Exception {
        long a = insertItem("A", 1_000);
        long b = insertItem("B", 1_000);
        List<Callable<PlaceOrderResult>> tasks = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            boolean forward = i % 2 == 0;
            tasks.add(() -> service.place(forward
                    ? order(UUID.randomUUID(), line("A", 1), line("B", 1))
                    : order(UUID.randomUUID(), line("B", 1), line("A", 1))));
        }

        List<Outcome<PlaceOrderResult>> outcomes = Concurrently.run(tasks);

        assertThat(outcomes).allMatch(Outcome::ok);
        assertThat(balanceOf(a)).isEqualTo(960);
        assertThat(balanceOf(b)).isEqualTo(960);
    }

    @Test
    void concurrentResendsCreateExactlyOneOrder() throws Exception {
        long pen = insertItem("PEN", 100);
        UUID id = UUID.randomUUID();

        List<Outcome<PlaceOrderResult>> outcomes = Concurrently.run(Concurrently.times(20,
                () -> service.place(order(id, line("PEN", 3)))));

        assertThat(outcomes).allMatch(Outcome::ok);
        assertThat(outcomes.stream().filter(o -> o.value().created())).hasSize(1);
        assertThat(outcomes.stream().map(o -> o.value().order().id()).distinct()).hasSize(1);
        assertThat(count("SELECT count(*) FROM orders")).isEqualTo(1);
        assertThat(balanceOf(pen)).isEqualTo(97);
    }

    @Test
    void concurrentCancellationsReturnBalanceOnce() throws Exception {
        long pen = insertItem("PEN", 10);
        long orderId = service.place(order(UUID.randomUUID(), line("PEN", 4))).order().id();

        List<Outcome<Boolean>> outcomes = Concurrently.run(Concurrently.times(10, () -> {
            service.cancel(orderId, null);
            return true;
        }));

        assertThat(outcomes.stream().filter(Outcome::ok)).hasSize(1);
        assertThat(outcomes.stream().filter(o -> !o.ok()))
                .allSatisfy(o -> assertThat(((BusinessException) o.error()).getCode()).isEqualTo("ORDER_ALREADY_CANCELLED"));
        assertThat(balanceOf(pen)).isEqualTo(10);
    }

    @Test
    void cancellationsAndNewOrdersInterleaveConsistently() throws Exception {
        long pen = insertItem("PEN", 30);
        List<Long> toCancel = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            toCancel.add(service.place(order(UUID.randomUUID(), line("PEN", 2))).order().id());
        }
        // Balance now 10. 10 cancellations give back 20; 20 new orders want 2 each (40).
        List<Callable<Boolean>> tasks = new ArrayList<>();
        toCancel.forEach(id -> tasks.add(() -> {
            service.cancel(id, null);
            return true;
        }));
        for (int i = 0; i < 20; i++) {
            tasks.add(() -> service.place(order(UUID.randomUUID(), line("PEN", 2))).created());
        }

        Concurrently.run(tasks);

        int confirmedQuantity = count("SELECT coalesce(sum(l.quantity), 0) FROM order_item l"
                + " JOIN orders o ON o.id = l.order_id WHERE o.status = 'CONFIRMED' AND l.item_id = ?", pen);
        assertThat(balanceOf(pen)).isGreaterThanOrEqualTo(0);
        assertThat(balanceOf(pen) + confirmedQuantity).as("conservation: nothing created or lost").isEqualTo(30);
    }
}

package br.com.balancecontrol.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.balancecontrol.AbstractIntegrationTest;
import br.com.balancecontrol.Concurrently;
import br.com.balancecontrol.Concurrently.Outcome;
import br.com.balancecontrol.common.error.BusinessException;
import br.com.balancecontrol.item.dto.AdjustBalanceRequest;
import br.com.balancecontrol.item.dto.CreateItemRequest;
import br.com.balancecontrol.item.dto.ItemResponse;
import br.com.balancecontrol.item.dto.UpdateItemRequest;
import br.com.balancecontrol.order.OrderOrigin;
import br.com.balancecontrol.order.OrderService;
import br.com.balancecontrol.order.dto.OrderLineRequest;
import br.com.balancecontrol.order.dto.PlaceOrderRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class ItemServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    ItemService service;

    @Autowired
    OrderService orders;

    @Test
    void createNormalizesUnitAndRejectsDuplicateCode() {
        ItemResponse created = service.create(new CreateItemRequest("NEW-1", "Notebook", " un ", 5));

        assertThat(created.unit()).isEqualTo("UN");
        assertThat(created.balance()).isEqualTo(5);
        assertThatThrownBy(() -> service.create(new CreateItemRequest("NEW-1", "Other", "CX", 1)))
                .hasFieldOrPropertyWithValue("code", "ITEM_CODE_ALREADY_EXISTS");
    }

    @Test
    void staleVersionIsRejectedWithCurrentState() {
        long id = insertItem("PEN", 10);
        service.update(id, new UpdateItemRequest("Blue pen", "UN", true, 0L));

        assertThatThrownBy(() -> service.update(id, new UpdateItemRequest("Red pen", "UN", true, 0L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ITEM_VERSION_CONFLICT")
                .satisfies(e -> assertThat(((BusinessException) e).getDetails())
                        .singleElement()
                        .isInstanceOfSatisfying(ItemResponse.class, current -> {
                            assertThat(current.description()).isEqualTo("Blue pen");
                            assertThat(current.version()).isEqualTo(1L);
                        }));
    }

    @Test
    void twoManagersSavingTheSameVersionAtOnceOnlyOneWins() throws Exception {
        long id = insertItem("PEN", 10);

        List<Outcome<ItemResponse>> outcomes = Concurrently.run(List.of(
                () -> service.update(id, new UpdateItemRequest("From manager A", "UN", true, 0L)),
                () -> service.update(id, new UpdateItemRequest("From manager B", "UN", true, 0L))));

        assertThat(outcomes.stream().filter(Outcome::ok)).hasSize(1);
        assertThat(outcomes.stream().filter(o -> !o.ok()).findFirst().orElseThrow().error())
                .hasFieldOrPropertyWithValue("code", "ITEM_VERSION_CONFLICT");
    }

    @Test
    void editingDescriptionNeverOverwritesBalanceChangedByAnOrder() {
        insertAgency("ORG-1");
        long id = insertItem("PEN", 10);
        ItemResponse seenByManager = service.get(id);

        orders.place(new PlaceOrderRequest(UUID.randomUUID(), "ORG-1", "user", OrderOrigin.PORTAL,
                List.of(new OrderLineRequest("PEN", 3))));
        ItemResponse saved = service.update(id,
                new UpdateItemRequest("New description", "UN", true, seenByManager.version()));

        assertThat(saved.description()).isEqualTo("New description");
        assertThat(balanceOf(id)).isEqualTo(7);
    }

    @Test
    void adjustmentsAreAtomicLoggedAndNeverNegative() throws Exception {
        long id = insertItem("PEN", 10);

        List<Outcome<ItemResponse>> outcomes = Concurrently.run(Concurrently.times(10,
                () -> service.adjustBalance(id, new AdjustBalanceRequest(5, "Stock entry"))));
        assertThat(outcomes).allMatch(Outcome::ok);
        assertThat(balanceOf(id)).as("concurrent deltas all applied").isEqualTo(60);

        assertThatThrownBy(() -> service.adjustBalance(id, new AdjustBalanceRequest(-61, "Write-off")))
                .hasFieldOrPropertyWithValue("code", "INSUFFICIENT_BALANCE");
        assertThat(service.adjustBalance(id, new AdjustBalanceRequest(-60, "Write-off")).balance()).isZero();
        assertThat(count("SELECT count(*) FROM item_balance_adjustment WHERE item_id = ?", id)).isEqualTo(11);
    }

    @Test
    void databaseRejectsNegativeBalanceEvenOutsideTheApplication() {
        long id = insertItem("PEN", 1);

        assertThatThrownBy(() -> jdbc.update("UPDATE item SET balance = -1 WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_item_balance_non_negative");
    }
}

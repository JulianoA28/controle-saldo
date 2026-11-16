package br.com.balancecontrol.dataload;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.balancecontrol.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Feeds the staging tables with one example of each legacy problem and checks where every
 * row ends up. Same procedure the docker compose load calls.
 */
class DataloadProcedureIntegrationTest extends AbstractIntegrationTest {

    static final String O1 = "11111111-1111-1111-1111-111111111111"; // good, confirmed
    static final String O2 = "22222222-2222-2222-2222-222222222222"; // resent (identical copy)
    static final String O3 = "33333333-3333-3333-3333-333333333333"; // unknown agency
    static final String O4 = "44444444-4444-4444-4444-444444444444"; // no items
    static final String O5 = "55555555-5555-5555-5555-555555555555"; // one good line + one unknown item
    static final String O6 = "66666666-6666-6666-6666-666666666666"; // invalid quantity
    static final String O7 = "77777777-7777-7777-7777-777777777777"; // cancelled
    static final String ORPHAN = "99999999-9999-9999-9999-999999999999";

    @Autowired
    DataloadQueries queries;

    @BeforeEach
    void stage() {
        jdbc.execute("TRUNCATE stg_orgaos, stg_itens, stg_pedidos, stg_pedido_itens RESTART IDENTITY");
        jdbc.update("INSERT INTO stg_orgaos (codigo, sigla, nome, uf) VALUES"
                + " ('ORG-1', 'AG1', 'Agency One', 'DF'), ('ORG-2', 'AG2', 'Agency Two', 'sp')");
        jdbc.update("INSERT INTO stg_itens (codigo, descricao, unidade, saldo_inicial, saldo_atual, ativo, data_cadastro) VALUES"
                + " ('PEN', 'Pen', ' un', '100', '90', 'S', '2022-01-31'),"   // dirty unit; consistent balance
                + " ('TAPE', 'Tape', 'Cx', '10', '10', 'N', '2021-05-01'),"   // inactive; diverges
                + " ('BAD', 'Bad', 'UN', 'abc', '1', 'S', '2021-05-01')");    // invalid balance
        jdbc.update("INSERT INTO stg_pedidos (id_externo, codigo_orgao, solicitante, origem, data_hora, situacao,"
                + " data_cancelamento, motivo_cancelamento) VALUES"
                + " (?, 'ORG-1', '123', 'PORTAL', '2024-01-10T10:00:00', 'CONFIRMADO', '', ''),"
                + " (?, 'ORG-2', 'SISTEMA', 'INTEGRACAO', '2024-01-11T10:00:00', 'CONFIRMADO', '', ''),"
                + " (?, 'ORG-2', 'SISTEMA', 'INTEGRACAO', '2024-01-11T10:00:00', 'CONFIRMADO', '', ''),"
                + " (?, 'ORG-404', '1', 'PORTAL', '2024-01-12T10:00:00', 'CONFIRMADO', '', ''),"
                + " (?, 'ORG-1', '1', 'PORTAL', '2024-01-13T10:00:00', 'CONFIRMADO', '', ''),"
                + " (?, 'ORG-1', '1', 'PORTAL', '2024-01-14T10:00:00', 'CONFIRMADO', '', ''),"
                + " (?, 'ORG-1', '1', 'PORTAL', '2024-01-15T10:00:00', 'CONFIRMADO', '', ''),"
                + " (?, 'ORG-1', '1', 'PORTAL', '2024-01-16T10:00:00', 'CANCELADO', '2024-01-17T08:00:00', 'Duplicated')",
                O1, O2, O2, O3, O4, O5, O6, O7);
        jdbc.update("INSERT INTO stg_pedido_itens (id_externo_pedido, codigo_item, quantidade) VALUES"
                + " (?, 'PEN', '10'),"
                + " (?, 'TAPE', '2'), (?, 'TAPE', '2'),"   // duplicated line of the resent order
                + " (?, 'PEN', '1'),"
                + " (?, 'PEN', '1'), (?, 'GHOST', '1'),"
                + " (?, 'PEN', '-5'),"
                + " (?, 'PEN', '7'),"
                + " (?, 'PEN', '1')",
                O1, O2, O2, O3, O5, O5, O6, O7, ORPHAN);
    }

    @Test
    void loadsValidRowsQuarantinesTheRestAndReconciles() {
        jdbc.execute("CALL dataload_process()");

        assertThat(count("SELECT count(*) FROM agency")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT state FROM agency WHERE code = 'ORG-2'", String.class)).isEqualTo("SP");

        assertThat(jdbc.queryForList("SELECT code, unit, balance, active FROM item ORDER BY code")).containsExactly(
                Map.of("code", "PEN", "unit", "UN", "balance", 90, "active", true),
                Map.of("code", "TAPE", "unit", "CX", "balance", 10, "active", false));

        assertThat(jdbc.queryForList("SELECT external_id::text FROM orders ORDER BY created_at", String.class))
                .containsExactly(O1, O2, O7);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE external_id = ?::uuid", String.class, O7))
                .isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT created_at = '2024-01-10T13:00:00Z'::timestamptz FROM orders"
                + " WHERE external_id = ?::uuid", Boolean.class, O1)).as("Brasília local time").isTrue();
        assertThat(count("SELECT count(*) FROM order_item")).isEqualTo(3);

        assertThat(reasons("pedidos.csv")).containsExactlyInAnyOrder(
                "DUPLICATE_RESEND", "AGENCY_NOT_FOUND", "NO_ITEMS", "INVALID_LINES", "INVALID_LINES");
        assertThat(reasons("pedido_itens.csv")).containsExactlyInAnyOrder(
                "DUPLICATE_ROW", "ORDER_REJECTED", "ORDER_REJECTED", "ITEM_NOT_FOUND", "INVALID_QUANTITY",
                "ORDER_NOT_FOUND");
        assertThat(reasons("itens.csv")).containsExactly("INVALID_BALANCE");

        // PEN: 100 - 10 (O1; O7 is cancelled) = 90 = legacy → consistent.
        // TAPE: 10 - 2 (O2) = 8 ≠ legacy 10 → divergence recorded, legacy balance kept.
        assertThat(jdbc.queryForList("SELECT item_code, legacy_balance, computed_balance FROM dataload_divergence"))
                .containsExactly(Map.of("item_code", "TAPE", "legacy_balance", 10, "computed_balance", 8L));

        DataloadSummary summary = queries.summary();
        assertThat(summary.loaded()).isTrue();
        assertThat(summary.stats().get("orders").asInt()).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM stg_pedidos")).as("staging cleared").isZero();
    }

    @Test
    void secondRunIsANoOp() {
        jdbc.execute("CALL dataload_process()");
        jdbc.execute("CALL dataload_process()");

        assertThat(count("SELECT count(*) FROM dataload_run")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM orders")).isEqualTo(3);
    }

    private List<String> reasons(String source) {
        return jdbc.queryForList("SELECT reason FROM dataload_rejection WHERE source = ?", String.class, source);
    }
}

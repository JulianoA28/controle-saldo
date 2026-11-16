-- Legacy data load. The CSVs are COPY'd into stg_* tables (all text, column names exactly as
-- in the files); dataload_process() validates them in SQL, moves valid rows to the domain
-- tables and records every rejected row with a reason. The whole load is one transaction.

-- Staging: UNLOGGED (fast, disposable); row_no follows file order (file line = row_no + 1).
CREATE UNLOGGED TABLE stg_orgaos (
    row_no bigint GENERATED ALWAYS AS IDENTITY,
    codigo text, sigla text, nome text, uf text
);
CREATE UNLOGGED TABLE stg_itens (
    row_no bigint GENERATED ALWAYS AS IDENTITY,
    codigo text, descricao text, unidade text, saldo_inicial text, saldo_atual text, ativo text, data_cadastro text
);
CREATE UNLOGGED TABLE stg_pedidos (
    row_no bigint GENERATED ALWAYS AS IDENTITY,
    id_externo text, codigo_orgao text, solicitante text, origem text, data_hora text,
    situacao text, data_cancelamento text, motivo_cancelamento text
);
CREATE UNLOGGED TABLE stg_pedido_itens (
    row_no bigint GENERATED ALWAYS AS IDENTITY,
    id_externo_pedido text, codigo_item text, quantidade text
);

-- Quarantine: every source row that did not make it into the domain tables, and why.
CREATE TABLE dataload_rejection (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source     varchar(40) NOT NULL,
    line_no    bigint      NOT NULL,
    source_key varchar(100),
    reason     varchar(40) NOT NULL,
    raw        jsonb       NOT NULL
);
-- Rejection list filtered by source/reason (summary and paginated browsing).
CREATE INDEX ix_dataload_rejection_source_reason ON dataload_rejection (source, reason, line_no);

-- Items whose legacy balance differs from initial balance minus confirmed orders.
CREATE TABLE dataload_divergence (
    item_id            bigint  PRIMARY KEY REFERENCES item (id),
    item_code          varchar(20) NOT NULL,
    initial_balance    integer NOT NULL,
    legacy_balance     integer NOT NULL,
    confirmed_quantity bigint  NOT NULL,
    computed_balance   bigint  NOT NULL
);

CREATE TABLE dataload_run (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    finished_at timestamptz NOT NULL DEFAULT now(),
    stats       jsonb       NOT NULL
);

-- Safe casts: NULL instead of an error. SQL does not guarantee short-circuit evaluation of
-- AND/OR, so "valid(x) AND x::int > 0" could still raise on bad input; CASE does guarantee order.
CREATE FUNCTION dataload_int(value text) RETURNS integer
LANGUAGE sql IMMUTABLE AS
$$ SELECT CASE WHEN pg_input_is_valid(btrim(value), 'integer') THEN btrim(value)::integer END $$;

CREATE FUNCTION dataload_uuid(value text) RETURNS uuid
LANGUAGE sql IMMUTABLE AS
$$ SELECT CASE WHEN pg_input_is_valid(btrim(value), 'uuid') THEN btrim(value)::uuid END $$;

CREATE FUNCTION dataload_timestamp(value text) RETURNS timestamp
LANGUAGE sql IMMUTABLE AS
$$ SELECT CASE WHEN pg_input_is_valid(btrim(value), 'timestamp') THEN btrim(value)::timestamp END $$;

CREATE FUNCTION dataload_date(value text) RETURNS date
LANGUAGE sql IMMUTABLE AS
$$ SELECT CASE WHEN pg_input_is_valid(btrim(value), 'date') THEN btrim(value)::date END $$;

CREATE PROCEDURE dataload_process()
LANGUAGE plpgsql
AS $$
DECLARE
    stats jsonb;
BEGIN
    IF EXISTS (SELECT 1 FROM dataload_run) THEN
        RAISE NOTICE 'Data load already done; skipping.';
        RETURN;
    END IF;
    IF EXISTS (SELECT 1 FROM agency) OR EXISTS (SELECT 1 FROM item) OR EXISTS (SELECT 1 FROM orders) THEN
        RAISE EXCEPTION 'Data load needs empty domain tables.';
    END IF;

    DROP TABLE IF EXISTS v_orgaos, v_itens, v_pedidos, v_pedido_itens;

    ---------------------------------------------------------------- agencies
    CREATE TEMP TABLE v_orgaos ON COMMIT DROP AS
    SELECT s.*,
           CASE
               WHEN nullif(btrim(codigo), '') IS NULL THEN 'MISSING_CODE'
               WHEN count(*) OVER (PARTITION BY btrim(codigo)) > 1 THEN 'DUPLICATE_CODE'
               WHEN nullif(btrim(nome), '') IS NULL OR nullif(btrim(sigla), '') IS NULL THEN 'MISSING_NAME'
               WHEN length(btrim(codigo)) > 20 OR length(btrim(sigla)) > 20 OR length(btrim(nome)) > 150 THEN 'VALUE_TOO_LONG'
               WHEN coalesce(upper(btrim(uf)), '') !~ '^[A-Z]{2}$' THEN 'INVALID_STATE'
           END AS reason
    FROM stg_orgaos s;

    INSERT INTO agency (code, acronym, name, state)
    SELECT btrim(codigo), btrim(sigla), btrim(nome), upper(btrim(uf)) FROM v_orgaos WHERE reason IS NULL;

    INSERT INTO dataload_rejection (source, line_no, source_key, reason, raw)
    SELECT 'orgaos.csv', row_no + 1, left(codigo, 100), reason, to_jsonb(v) - 'row_no' - 'reason'
    FROM v_orgaos v WHERE reason IS NOT NULL;

    ---------------------------------------------------------------- items
    CREATE TEMP TABLE v_itens ON COMMIT DROP AS
    WITH t AS (
        SELECT s.*, dataload_int(saldo_inicial) AS initial_balance, dataload_int(saldo_atual) AS legacy_balance,
               dataload_date(data_cadastro) AS registered_on
        FROM stg_itens s
    )
    SELECT t.*,
           CASE
               WHEN nullif(btrim(codigo), '') IS NULL THEN 'MISSING_CODE'
               WHEN count(*) OVER (PARTITION BY btrim(codigo)) > 1 THEN 'DUPLICATE_CODE'
               WHEN nullif(btrim(descricao), '') IS NULL OR nullif(btrim(unidade), '') IS NULL THEN 'MISSING_DESCRIPTION_OR_UNIT'
               WHEN length(btrim(codigo)) > 20 OR length(btrim(descricao)) > 200 OR length(btrim(unidade)) > 10 THEN 'VALUE_TOO_LONG'
               WHEN coalesce(initial_balance, -1) < 0 OR coalesce(legacy_balance, -1) < 0 THEN 'INVALID_BALANCE'
               WHEN coalesce(upper(btrim(ativo)), '') NOT IN ('S', 'N') THEN 'INVALID_ACTIVE_FLAG'
               WHEN registered_on IS NULL THEN 'INVALID_DATE'
           END AS reason
    FROM t;

    INSERT INTO item (code, description, unit, balance, active, registered_on)
    SELECT btrim(codigo), btrim(descricao), upper(btrim(unidade)), legacy_balance, upper(btrim(ativo)) = 'S', registered_on
    FROM v_itens WHERE reason IS NULL;

    INSERT INTO dataload_rejection (source, line_no, source_key, reason, raw)
    SELECT 'itens.csv', row_no + 1, left(codigo, 100), reason,
           to_jsonb(v) - 'row_no' - 'reason' - 'initial_balance' - 'legacy_balance' - 'registered_on'
    FROM v_itens v WHERE reason IS NOT NULL;

    ---------------------------------------------------------------- orders
    -- Identical rows with the same id = integration resend: keep the first, log the others.
    -- Same id with different content = genuine conflict: reject every version.
    CREATE TEMP TABLE v_pedidos ON COMMIT DROP AS
    WITH t AS (
        SELECT s.*, dataload_uuid(id_externo) AS external_id,
               dataload_timestamp(data_hora) AS created_local,
               dataload_timestamp(data_cancelamento) AS cancelled_local,
               md5(row(codigo_orgao, solicitante, origem, data_hora, situacao,
                       data_cancelamento, motivo_cancelamento)::text) AS content
        FROM stg_pedidos s
    ), d AS (
        SELECT t.*,
               -- more than one distinct content for the same id (count(DISTINCT) is not allowed in windows)
               min(content) OVER (PARTITION BY btrim(id_externo)) <> max(content) OVER (PARTITION BY btrim(id_externo)) AS conflicting,
               row_number() OVER (PARTITION BY btrim(id_externo), content ORDER BY row_no) AS copy_no
        FROM t
    )
    SELECT d.row_no, d.id_externo, d.codigo_orgao, d.solicitante, d.origem, d.data_hora, d.situacao,
           d.data_cancelamento, d.motivo_cancelamento, d.external_id, d.created_local, d.cancelled_local,
           a.id AS agency_id,
           CASE
               WHEN d.external_id IS NULL THEN 'INVALID_EXTERNAL_ID'
               WHEN d.conflicting THEN 'CONFLICTING_DUPLICATE'
               WHEN d.copy_no > 1 THEN 'DUPLICATE_RESEND'
               WHEN a.id IS NULL THEN 'AGENCY_NOT_FOUND'
               WHEN nullif(btrim(d.solicitante), '') IS NULL OR length(btrim(d.solicitante)) > 100 THEN 'INVALID_REQUESTER'
               WHEN coalesce(btrim(d.origem), '') NOT IN ('PORTAL', 'INTEGRACAO') THEN 'INVALID_ORIGIN'
               WHEN coalesce(btrim(d.situacao), '') NOT IN ('CONFIRMADO', 'CANCELADO') THEN 'INVALID_STATUS'
               WHEN d.created_local IS NULL THEN 'INVALID_DATE'
               WHEN (btrim(d.situacao) = 'CANCELADO') <> (d.cancelled_local IS NOT NULL)
                 OR (nullif(btrim(d.data_cancelamento), '') IS NOT NULL AND d.cancelled_local IS NULL)
                 OR coalesce(length(btrim(d.motivo_cancelamento)), 0) > 200 THEN 'INCONSISTENT_CANCELLATION'
           END AS reason
    FROM d
    LEFT JOIN agency a ON a.code = btrim(d.codigo_orgao);
    CREATE INDEX ON v_pedidos (external_id);
    ANALYZE v_pedidos;

    ---------------------------------------------------------------- order lines
    CREATE TEMP TABLE v_pedido_itens ON COMMIT DROP AS
    WITH t AS (
        SELECT s.*, dataload_uuid(id_externo_pedido) AS external_id, dataload_int(quantidade) AS quantity
        FROM stg_pedido_itens s
    ), d AS (
        SELECT t.*,
               min(btrim(quantidade)) OVER w <> max(btrim(quantidade)) OVER w AS conflicting,
               row_number() OVER (PARTITION BY btrim(id_externo_pedido), btrim(codigo_item), btrim(quantidade)
                                  ORDER BY row_no) AS copy_no
        FROM t
        WINDOW w AS (PARTITION BY btrim(id_externo_pedido), btrim(codigo_item))
    ), known_orders AS (
        SELECT DISTINCT external_id FROM v_pedidos WHERE external_id IS NOT NULL
    )
    SELECT d.row_no, d.id_externo_pedido, d.codigo_item, d.quantidade, d.external_id, d.quantity,
           i.id AS item_id,
           CASE
               WHEN k.external_id IS NULL THEN 'ORDER_NOT_FOUND'
               WHEN i.id IS NULL THEN 'ITEM_NOT_FOUND'
               WHEN coalesce(d.quantity, 0) <= 0 THEN 'INVALID_QUANTITY'
               WHEN d.conflicting THEN 'CONFLICTING_DUPLICATE'
               WHEN d.copy_no > 1 THEN 'DUPLICATE_ROW'
           END AS reason
    FROM d
    LEFT JOIN known_orders k ON k.external_id = d.external_id
    LEFT JOIN item i ON i.code = btrim(d.codigo_item);
    CREATE INDEX ON v_pedido_itens (external_id);
    ANALYZE v_pedido_itens;

    -- R2 also for legacy data: an order with any invalid line is rejected whole.
    UPDATE v_pedidos v SET reason = 'INVALID_LINES'
    WHERE v.reason IS NULL
      AND EXISTS (SELECT 1 FROM v_pedido_itens l
                  WHERE l.external_id = v.external_id
                    AND l.reason IN ('ITEM_NOT_FOUND', 'INVALID_QUANTITY', 'CONFLICTING_DUPLICATE'));

    -- An order must have at least one item.
    UPDATE v_pedidos v SET reason = 'NO_ITEMS'
    WHERE v.reason IS NULL
      AND NOT EXISTS (SELECT 1 FROM v_pedido_itens l WHERE l.external_id = v.external_id AND l.reason IS NULL);

    -- Remaining good lines of rejected orders follow their order.
    UPDATE v_pedido_itens l SET reason = 'ORDER_REJECTED'
    WHERE l.reason IS NULL
      AND NOT EXISTS (SELECT 1 FROM v_pedidos v WHERE v.external_id = l.external_id AND v.reason IS NULL);

    -- Legacy timestamps have no zone: they are local time in Brasília.
    INSERT INTO orders (external_id, agency_id, requester, origin, status, created_at, cancelled_at, cancellation_reason)
    SELECT v.external_id, v.agency_id, btrim(v.solicitante),
           CASE btrim(v.origem) WHEN 'INTEGRACAO' THEN 'INTEGRATION' ELSE 'PORTAL' END,
           CASE btrim(v.situacao) WHEN 'CANCELADO' THEN 'CANCELLED' ELSE 'CONFIRMED' END,
           v.created_local AT TIME ZONE 'America/Sao_Paulo',
           v.cancelled_local AT TIME ZONE 'America/Sao_Paulo',
           nullif(btrim(v.motivo_cancelamento), '')
    FROM v_pedidos v
    WHERE v.reason IS NULL
    ORDER BY v.created_local, v.row_no;

    INSERT INTO order_item (order_id, item_id, quantity)
    SELECT o.id, l.item_id, l.quantity
    FROM v_pedido_itens l
    JOIN orders o ON o.external_id = l.external_id
    WHERE l.reason IS NULL;

    INSERT INTO dataload_rejection (source, line_no, source_key, reason, raw)
    SELECT 'pedidos.csv', row_no + 1, left(id_externo, 100), reason,
           to_jsonb(v) - 'row_no' - 'reason' - 'agency_id' - 'external_id' - 'created_local' - 'cancelled_local'
    FROM v_pedidos v WHERE reason IS NOT NULL;

    INSERT INTO dataload_rejection (source, line_no, source_key, reason, raw)
    SELECT 'pedido_itens.csv', row_no + 1, left(id_externo_pedido, 100), reason,
           to_jsonb(l) - 'row_no' - 'reason' - 'item_id' - 'external_id' - 'quantity'
    FROM v_pedido_itens l WHERE reason IS NOT NULL;

    ---------------------------------------------------------------- reconciliation
    -- Legacy current balance is loaded as the balance (ADR 0002). Check it against
    -- "initial - confirmed orders"; any divergence is recorded for audit, never silently "fixed".
    INSERT INTO dataload_divergence (item_id, item_code, initial_balance, legacy_balance, confirmed_quantity, computed_balance)
    SELECT i.id, i.code, v.initial_balance, i.balance, coalesce(c.qty, 0), v.initial_balance - coalesce(c.qty, 0)
    FROM v_itens v
    JOIN item i ON i.code = btrim(v.codigo)
    LEFT JOIN (
        SELECT l.item_id, sum(l.quantity) AS qty
        FROM order_item l JOIN orders o ON o.id = l.order_id
        WHERE o.status = 'CONFIRMED'
        GROUP BY l.item_id
    ) c ON c.item_id = i.id
    WHERE v.reason IS NULL
      AND v.initial_balance - coalesce(c.qty, 0) <> i.balance;

    SELECT jsonb_build_object(
        'agencies', (SELECT count(*) FROM agency),
        'items', (SELECT count(*) FROM item),
        'orders', (SELECT count(*) FROM orders),
        'orderLines', (SELECT count(*) FROM order_item),
        'rejections', (SELECT count(*) FROM dataload_rejection),
        'divergentItems', (SELECT count(*) FROM dataload_divergence),
        'negativeIfRecalculated', (SELECT count(*) FROM dataload_divergence WHERE computed_balance < 0)
    ) INTO stats;

    INSERT INTO dataload_run (stats) VALUES (stats);
    TRUNCATE stg_orgaos, stg_itens, stg_pedidos, stg_pedido_itens;
    RAISE NOTICE 'Data load finished: %', stats;
END;
$$;

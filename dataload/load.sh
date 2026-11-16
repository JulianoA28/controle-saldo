#!/bin/sh
# Loads the legacy CSVs into PostgreSQL. Runs once as a docker compose service.
#   1. waits until Flyway (run by the backend) has created the schema;
#   2. skips if a load already finished (dataload_run has a row);
#   3. COPYs the CSVs into stg_* tables and CALLs dataload_process(), which does
#      all validation, quarantine and reconciliation in one transaction.
set -eu

DATA_DIR="${DATA_DIR:-/data}"
WORK_DIR="${WORK_DIR:-/tmp/dataload}"
PSQL="psql -v ON_ERROR_STOP=1 -X -q"

echo "[dataload] waiting for the schema (Flyway runs in the backend)..."
tries=0
until [ "$($PSQL -tAc "SELECT to_regprocedure('dataload_process()') IS NOT NULL" 2>/dev/null || true)" = "t" ]; do
    tries=$((tries + 1))
    if [ "$tries" -gt 120 ]; then
        echo "[dataload] schema not ready after 4 minutes; giving up." >&2
        exit 1
    fi
    sleep 2
done

if [ "$($PSQL -tAc "SELECT EXISTS (SELECT 1 FROM dataload_run)")" = "t" ]; then
    echo "[dataload] already loaded; nothing to do."
    exit 0
fi

# Accept either the extracted CSVs or the original zip in the data folder.
SRC="$DATA_DIR"
if [ ! -f "$SRC/pedidos.csv" ]; then
    ZIP="$(ls "$DATA_DIR"/*.zip 2>/dev/null | head -n 1 || true)"
    if [ -z "$ZIP" ]; then
        echo "[dataload] no CSVs or zip found in $DATA_DIR; skipping (the app works with an empty database)."
        exit 0
    fi
    echo "[dataload] extracting $ZIP..."
    mkdir -p "$WORK_DIR"
    unzip -oq "$ZIP" -d "$WORK_DIR"
    SRC="$WORK_DIR"
fi

for f in orgaos.csv itens.csv pedidos.csv pedido_itens.csv; do
    [ -f "$SRC/$f" ] || { echo "[dataload] missing $SRC/$f" >&2; exit 1; }
done

echo "[dataload] copying CSVs into staging and processing (takes a few minutes)..."
start=$(date +%s)
$PSQL <<SQL
TRUNCATE stg_orgaos, stg_itens, stg_pedidos, stg_pedido_itens RESTART IDENTITY;
\copy stg_orgaos (codigo, sigla, nome, uf) FROM '$SRC/orgaos.csv' WITH (FORMAT csv, DELIMITER ';', HEADER true, ENCODING 'UTF8')
\copy stg_itens (codigo, descricao, unidade, saldo_inicial, saldo_atual, ativo, data_cadastro) FROM '$SRC/itens.csv' WITH (FORMAT csv, DELIMITER ';', HEADER true, ENCODING 'UTF8')
\copy stg_pedidos (id_externo, codigo_orgao, solicitante, origem, data_hora, situacao, data_cancelamento, motivo_cancelamento) FROM '$SRC/pedidos.csv' WITH (FORMAT csv, DELIMITER ';', HEADER true, ENCODING 'UTF8')
\copy stg_pedido_itens (id_externo_pedido, codigo_item, quantidade) FROM '$SRC/pedido_itens.csv' WITH (FORMAT csv, DELIMITER ';', HEADER true, ENCODING 'UTF8')
SET client_min_messages = warning;
CALL dataload_process();
SQL
echo "[dataload] done in $(( $(date +%s) - start ))s:"
$PSQL -tAc "SELECT stats FROM dataload_run ORDER BY id DESC LIMIT 1"
$PSQL -c "SELECT source, reason, count(*) FROM dataload_rejection GROUP BY 1, 2 ORDER BY 1, 3 DESC"

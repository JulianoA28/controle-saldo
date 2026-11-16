package br.com.balancecontrol.dataload;

import br.com.balancecontrol.common.web.PageResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only views over the load report tables. Plain JDBC: these are reports, not domain
 * entities, and the rows are written by the SQL procedure, never by JPA.
 */
@Repository
public class DataloadQueries {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public DataloadQueries(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public DataloadSummary summary() {
        List<JsonNode> runs = jdbc.query("SELECT stats::text FROM dataload_run ORDER BY id DESC LIMIT 1",
                (rs, n) -> parse(rs.getString(1)));
        List<java.time.Instant> finished = jdbc.query("SELECT finished_at FROM dataload_run ORDER BY id DESC LIMIT 1",
                (rs, n) -> rs.getTimestamp(1).toInstant());
        List<DataloadSummary.ReasonCount> reasons = jdbc.query(
                "SELECT source, reason, count(*) FROM dataload_rejection GROUP BY source, reason ORDER BY source, count(*) DESC",
                (rs, n) -> new DataloadSummary.ReasonCount(rs.getString(1), rs.getString(2), rs.getLong(3)));
        return new DataloadSummary(!runs.isEmpty(), finished.isEmpty() ? null : finished.get(0),
                runs.isEmpty() ? null : runs.get(0), reasons);
    }

    public PageResponse<RejectionRow> rejections(String source, String reason, Pageable page) {
        StringBuilder where = new StringBuilder(" WHERE true");
        List<Object> args = new ArrayList<>();
        if (source != null && !source.isBlank()) {
            where.append(" AND source = ?");
            args.add(source);
        }
        if (reason != null && !reason.isBlank()) {
            where.append(" AND reason = ?");
            args.add(reason);
        }
        long total = jdbc.queryForObject("SELECT count(*) FROM dataload_rejection" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(page.getPageSize());
        pageArgs.add(page.getOffset());
        List<RejectionRow> rows = jdbc.query(
                "SELECT source, line_no, source_key, reason, raw::text FROM dataload_rejection" + where
                        + " ORDER BY source, reason, line_no LIMIT ? OFFSET ?",
                (rs, n) -> new RejectionRow(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        parse(rs.getString(5))),
                pageArgs.toArray());
        return page(rows, page, total);
    }

    public PageResponse<DivergenceRow> divergences(boolean onlyNegative, Pageable page) {
        String where = onlyNegative ? " WHERE computed_balance < 0" : "";
        long total = jdbc.queryForObject("SELECT count(*) FROM dataload_divergence" + where, Long.class);
        List<DivergenceRow> rows = jdbc.query(
                "SELECT item_id, item_code, initial_balance, legacy_balance, confirmed_quantity, computed_balance"
                        + " FROM dataload_divergence" + where + " ORDER BY computed_balance, item_code LIMIT ? OFFSET ?",
                (rs, n) -> new DivergenceRow(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getInt(4),
                        rs.getLong(5), rs.getLong(6)),
                page.getPageSize(), page.getOffset());
        return page(rows, page, total);
    }

    private static <T> PageResponse<T> page(List<T> rows, Pageable page, long total) {
        int totalPages = (int) Math.ceil((double) total / page.getPageSize());
        return new PageResponse<>(rows, page.getPageNumber(), page.getPageSize(), total, totalPages);
    }

    private JsonNode parse(String text) {
        try {
            return json.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid JSON in load report", e);
        }
    }
}

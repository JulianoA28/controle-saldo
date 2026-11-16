package br.com.balancecontrol.order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    /** List page: agency fetched in the same query (no N+1); lines are not loaded. */
    @Override
    @EntityGraph(attributePaths = "agency")
    Page<Order> findAll(Specification<Order> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"agency", "lines", "lines.item"})
    Optional<Order> findWithLinesById(Long id);

    @EntityGraph(attributePaths = {"agency", "lines", "lines.item"})
    Optional<Order> findWithLinesByExternalId(UUID externalId);

    boolean existsByExternalId(UUID externalId);

    /**
     * R3: the status check and the change are one statement. Two concurrent cancellations:
     * the second waits for the row lock, re-evaluates the WHERE, and updates 0 rows.
     */
    @Modifying
    @Query(value = "UPDATE orders SET status = 'CANCELLED', cancelled_at = :at, cancellation_reason = :reason"
            + " WHERE id = :id AND status = 'CONFIRMED'", nativeQuery = true)
    int cancel(@Param("id") long id, @Param("at") Instant at, @Param("reason") String reason);

    /** Lines to give back, sorted by item id (same lock order as placement: no deadlocks). */
    @Query("select l.item.id as itemId, l.quantity as quantity from OrderItem l"
            + " where l.order.id = :orderId order by l.item.id")
    List<LineQuantity> linesToRestore(@Param("orderId") long orderId);

    interface LineQuantity {
        Long getItemId();

        int getQuantity();
    }
}

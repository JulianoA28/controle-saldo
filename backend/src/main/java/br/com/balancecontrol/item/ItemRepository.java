package br.com.balancecontrol.item;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemRepository extends JpaRepository<Item, Long> {

    Optional<Item> findByCode(String code);

    boolean existsByCode(String code);

    List<Item> findByCodeIn(Collection<String> codes);

    // 50k rows: a filtered sequential scan takes a few ms, so no trigram index for now.
    @Query("select i from Item i where (lower(i.code) like :pattern or lower(i.description) like :pattern)"
            + " and (:active is null or i.active = :active)")
    Page<Item> search(@Param("pattern") String pattern, @Param("active") Boolean active, Pageable pageable);

    /**
     * R2/R4: atomic conditional debit. The row lock taken by the UPDATE serializes concurrent
     * orders on the same item; the WHERE re-checks the balance after waiting for the lock.
     *
     * @return 1 if debited, 0 if the balance was insufficient
     */
    @Modifying
    @Query(value = "UPDATE item SET balance = balance - :quantity WHERE id = :id AND balance >= :quantity",
            nativeQuery = true)
    int debit(@Param("id") long id, @Param("quantity") int quantity);

    /** R3: returns quantities of a cancelled order. */
    @Modifying
    @Query(value = "UPDATE item SET balance = balance + :quantity WHERE id = :id", nativeQuery = true)
    int credit(@Param("id") long id, @Param("quantity") int quantity);

    /**
     * Manual adjustment by delta (positive or negative). Deltas commute, so two managers
     * adjusting at the same time both apply: nothing is lost.
     *
     * @return 1 if applied, 0 if the item does not exist or the result would be negative
     */
    @Modifying
    @Query(value = "UPDATE item SET balance = balance + :delta WHERE id = :id AND balance + :delta >= 0",
            nativeQuery = true)
    int adjust(@Param("id") long id, @Param("delta") int delta);

    /** Reads the balance straight from the database, bypassing the persistence context. */
    @Query(value = "SELECT balance FROM item WHERE id = :id", nativeQuery = true)
    Optional<Integer> currentBalance(@Param("id") long id);
}

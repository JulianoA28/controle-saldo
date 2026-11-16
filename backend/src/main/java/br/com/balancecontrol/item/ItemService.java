package br.com.balancecontrol.item;

import br.com.balancecontrol.common.db.DbErrors;
import br.com.balancecontrol.common.error.BusinessRuleException;
import br.com.balancecontrol.common.error.ConflictException;
import br.com.balancecontrol.common.error.InvalidRequestException;
import br.com.balancecontrol.common.error.NotFoundException;
import br.com.balancecontrol.common.web.PageResponse;
import br.com.balancecontrol.common.web.SearchPattern;
import br.com.balancecontrol.item.dto.AdjustBalanceRequest;
import br.com.balancecontrol.item.dto.AdjustmentResponse;
import br.com.balancecontrol.item.dto.CreateItemRequest;
import br.com.balancecontrol.item.dto.ItemResponse;
import br.com.balancecontrol.item.dto.UpdateItemRequest;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Item use cases. Transactions are explicit ({@link TransactionTemplate}) where a failure must be
 * translated after rollback, using a fresh transaction to read the current state.
 */
@Service
public class ItemService {

    private final ItemRepository items;
    private final ItemBalanceAdjustmentRepository adjustments;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public ItemService(ItemRepository items, ItemBalanceAdjustmentRepository adjustments,
                       PlatformTransactionManager txManager) {
        this.items = items;
        this.adjustments = adjustments;
        this.tx = new TransactionTemplate(txManager);
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
    }

    public PageResponse<ItemResponse> search(String text, Boolean active, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("code"));
        return readTx.execute(s ->
                PageResponse.of(items.search(SearchPattern.contains(text), active, sorted), ItemResponse::from));
    }

    public ItemResponse get(long id) {
        return readTx.execute(s -> ItemResponse.from(find(id)));
    }

    public ItemResponse create(CreateItemRequest request) {
        String code = request.code().trim();
        try {
            return tx.execute(s -> {
                if (items.existsByCode(code)) {
                    throw codeTaken(code);
                }
                Item item = new Item(code, request.description(), request.unit(), request.initialBalance());
                return ItemResponse.from(items.saveAndFlush(item));
            });
        } catch (DataIntegrityViolationException e) {
            // Two creations with the same code racing past the existence check.
            if (DbErrors.isViolationOf(e, "uk_item_code")) {
                throw codeTaken(code);
            }
            throw e;
        }
    }

    /**
     * R5: edit with optimistic locking. Rejected with 409 (and the current state in
     * {@code details}) when the version sent is not the current one — either because it was
     * already stale (explicit check) or because another edit committed between our read and
     * our write ({@code @Version} check on flush).
     */
    public ItemResponse update(long id, UpdateItemRequest request) {
        try {
            return tx.execute(s -> {
                Item item = find(id);
                if (item.getVersion() != request.version()) {
                    throw versionConflict(ItemResponse.from(item));
                }
                item.edit(request.description(), request.unit(), request.active());
                items.flush();
                return ItemResponse.from(item);
            });
        } catch (ObjectOptimisticLockingFailureException e) {
            throw versionConflict(get(id));
        }
    }

    /** Manual balance change by delta, atomic in SQL and recorded in the adjustment history. */
    public ItemResponse adjustBalance(long id, AdjustBalanceRequest request) {
        if (request.delta() == 0) {
            throw new InvalidRequestException("ZERO_ADJUSTMENT", "The adjustment delta cannot be zero.");
        }
        return tx.execute(s -> {
            Item item = find(id);
            if (items.adjust(id, request.delta()) == 0) {
                int available = items.currentBalance(id).orElse(0);
                throw new BusinessRuleException("INSUFFICIENT_BALANCE",
                        "The adjustment would make the balance negative.",
                        List.of(Map.of("itemCode", item.getCode(), "delta", request.delta(), "available", available)));
            }
            // We hold the row lock until commit, so this read is exactly our result.
            int balanceAfter = items.currentBalance(id).orElseThrow();
            adjustments.save(new ItemBalanceAdjustment(id, request.delta(), balanceAfter, request.reason()));
            return ItemResponse.from(item).withBalance(balanceAfter);
        });
    }

    public PageResponse<AdjustmentResponse> adjustments(long id, Pageable pageable) {
        return readTx.execute(s -> {
            find(id);
            return PageResponse.of(adjustments.findByItemIdOrderByCreatedAtDescIdDesc(id, pageable),
                    AdjustmentResponse::from);
        });
    }

    private Item find(long id) {
        return items.findById(id)
                .orElseThrow(() -> new NotFoundException("ITEM_NOT_FOUND", "Item " + id + " not found."));
    }

    private static ConflictException codeTaken(String code) {
        return new ConflictException("ITEM_CODE_ALREADY_EXISTS", "An item with code " + code + " already exists.");
    }

    private static ConflictException versionConflict(ItemResponse current) {
        return new ConflictException("ITEM_VERSION_CONFLICT",
                "This item was changed by someone else. Review the current values and reapply your changes.",
                List.of(current));
    }
}

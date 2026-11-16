package br.com.balancecontrol.order;

import br.com.balancecontrol.agency.Agency;
import br.com.balancecontrol.agency.AgencyRepository;
import br.com.balancecontrol.common.db.DbErrors;
import br.com.balancecontrol.common.error.BusinessRuleException;
import br.com.balancecontrol.common.error.ConflictException;
import br.com.balancecontrol.common.error.InvalidRequestException;
import br.com.balancecontrol.common.error.NotFoundException;
import br.com.balancecontrol.common.web.PageResponse;
import br.com.balancecontrol.item.Item;
import br.com.balancecontrol.item.ItemRepository;
import br.com.balancecontrol.order.dto.OrderLineRequest;
import br.com.balancecontrol.order.dto.OrderResponse;
import br.com.balancecontrol.order.dto.OrderSummaryResponse;
import br.com.balancecontrol.order.dto.PlaceOrderRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Order placement and cancellation. How each rule is guaranteed:
 * <ul>
 *   <li>R2 all-or-nothing: one transaction; any failed debit throws and rolls everything back.</li>
 *   <li>R4 concurrency: conditional {@code UPDATE ... WHERE balance >= q} per item, in item id
 *       order so two orders over the same items always lock rows in the same sequence.</li>
 *   <li>R6 idempotency: {@code UNIQUE (external_id)}; the order row is inserted before any
 *       debit, so a concurrent resend blocks on the index and then gets a unique violation,
 *       which is answered with the existing order.</li>
 *   <li>R3 cancel once: conditional status update; 0 rows means it was already cancelled.</li>
 * </ul>
 */
@Service
public class OrderService {

    private static final String EXTERNAL_ID_CONSTRAINT = "uk_orders_external_id";

    private final OrderRepository orders;
    private final AgencyRepository agencies;
    private final ItemRepository items;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public OrderService(OrderRepository orders, AgencyRepository agencies, ItemRepository items,
                        PlatformTransactionManager txManager) {
        this.orders = orders;
        this.agencies = agencies;
        this.items = items;
        this.tx = new TransactionTemplate(txManager);
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
    }

    public PlaceOrderResult place(PlaceOrderRequest request) {
        Map<String, Integer> requested = requestedQuantities(request);

        // Fast path for the common resend: no write attempted.
        PlaceOrderResult replay = readTx.execute(s -> orders.findWithLinesByExternalId(request.externalId())
                .map(existing -> replay(existing, request, requested))
                .orElse(null));
        if (replay != null) {
            return replay;
        }

        try {
            return tx.execute(s -> new PlaceOrderResult(OrderResponse.from(create(request, requested)), true));
        } catch (DataIntegrityViolationException e) {
            if (!DbErrors.isViolationOf(e, EXTERNAL_ID_CONSTRAINT)) {
                throw e;
            }
            // Lost the race against a concurrent request with the same id: that one committed.
            return readTx.execute(s -> replay(orders.findWithLinesByExternalId(request.externalId()).orElseThrow(),
                    request, requested));
        }
    }

    public void cancel(long id, String reason) {
        tx.executeWithoutResult(s -> {
            String cleanReason = reason == null || reason.isBlank() ? null : reason.trim();
            if (orders.cancel(id, Instant.now(), cleanReason) == 0) {
                if (!orders.existsById(id)) {
                    throw notFound(id);
                }
                throw new ConflictException("ORDER_ALREADY_CANCELLED", "Order " + id + " is already cancelled.");
            }
            for (OrderRepository.LineQuantity line : orders.linesToRestore(id)) {
                items.credit(line.getItemId(), line.getQuantity());
            }
        });
    }

    public OrderResponse get(long id) {
        return readTx.execute(s -> orders.findWithLinesById(id).map(OrderResponse::from)
                .orElseThrow(() -> notFound(id)));
    }

    public PageResponse<OrderSummaryResponse> list(Long agencyId, OrderStatus status, UUID externalId,
                                                   Pageable pageable) {
        // Fixed sort matching ix_orders_created_at / ix_orders_agency_created_at.
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return readTx.execute(s -> PageResponse.of(
                orders.findAll(OrderSpecs.filter(agencyId, status, externalId), sorted),
                OrderSummaryResponse::from));
    }

    private Order create(PlaceOrderRequest request, Map<String, Integer> requested) {
        Agency agency = agencies.findByCode(request.agencyCode().trim())
                .orElseThrow(() -> new BusinessRuleException("AGENCY_NOT_FOUND",
                        "Agency " + request.agencyCode() + " does not exist."));
        List<Item> lineItems = loadOrderableItems(requested.keySet());

        // Insert first: a concurrent resend now blocks on the unique index before touching any item.
        Order order = orders.saveAndFlush(new Order(request.externalId(), agency, request.requester(), request.origin()));

        List<Object> shortages = new ArrayList<>();
        lineItems.sort(Comparator.comparing(Item::getId));
        for (Item item : lineItems) {
            int quantity = requested.get(item.getCode());
            if (items.debit(item.getId(), quantity) == 0) {
                shortages.add(Map.of("itemCode", item.getCode(), "requested", quantity,
                        "available", items.currentBalance(item.getId()).orElse(0)));
            } else {
                order.addLine(item, quantity);
            }
        }
        if (!shortages.isEmpty()) {
            // Rolls back the order row and every debit already made (R2).
            throw new BusinessRuleException("INSUFFICIENT_BALANCE",
                    "Not enough balance for some items. Nothing was reserved.", shortages);
        }
        orders.flush();
        return order;
    }

    private List<Item> loadOrderableItems(java.util.Set<String> codes) {
        Map<String, Item> found = items.findByCodeIn(codes).stream()
                .collect(Collectors.toMap(Item::getCode, Function.identity()));
        List<Object> missing = codes.stream().filter(c -> !found.containsKey(c)).sorted().collect(Collectors.toList());
        if (!missing.isEmpty()) {
            throw new BusinessRuleException("ITEM_NOT_FOUND", "Some items do not exist.", missing);
        }
        List<Object> inactive = found.values().stream().filter(i -> !i.isActive())
                .map(Item::getCode).sorted().collect(Collectors.toList());
        if (!inactive.isEmpty()) {
            throw new BusinessRuleException("ITEM_INACTIVE", "Some items are inactive and cannot be ordered.", inactive);
        }
        return new ArrayList<>(found.values());
    }

    /**
     * Same id, same content: return the existing order (whatever its current status).
     * Same id, different content: a client bug — refuse instead of guessing which one is right.
     */
    private PlaceOrderResult replay(Order existing, PlaceOrderRequest request, Map<String, Integer> requested) {
        Map<String, Integer> stored = existing.getLines().stream()
                .collect(Collectors.toMap(l -> l.getItem().getCode(), OrderItem::getQuantity));
        boolean sameContent = existing.getAgency().getCode().equals(request.agencyCode().trim())
                && stored.equals(requested);
        if (!sameContent) {
            throw new ConflictException("IDEMPOTENCY_KEY_REUSED",
                    "External id " + request.externalId() + " was already used for a different order.",
                    List.of(Map.of("orderId", existing.getId())));
        }
        return new PlaceOrderResult(OrderResponse.from(existing), false);
    }

    private static Map<String, Integer> requestedQuantities(PlaceOrderRequest request) {
        Map<String, Integer> requested = new LinkedHashMap<>();
        List<Object> duplicated = new ArrayList<>();
        for (OrderLineRequest line : request.items()) {
            if (requested.put(line.itemCode().trim(), line.quantity()) != null) {
                duplicated.add(line.itemCode().trim());
            }
        }
        if (!duplicated.isEmpty()) {
            throw new InvalidRequestException("DUPLICATE_ITEM",
                    "Each item may appear only once per order; combine the quantities.", duplicated);
        }
        return requested;
    }

    private static NotFoundException notFound(long id) {
        return new NotFoundException("ORDER_NOT_FOUND", "Order " + id + " not found.");
    }
}

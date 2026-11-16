package br.com.balancecontrol.order;

import br.com.balancecontrol.agency.Agency;
import br.com.balancecontrol.item.Item;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;

/**
 * An order is created already CONFIRMED: it only exists if every line was debited (R2).
 * Status changes go through a conditional SQL update ({@link OrderRepository#cancel}),
 * so this entity has no setters for them.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agency_id", nullable = false, updatable = false)
    private Agency agency;

    @Column(nullable = false, updatable = false)
    private String requester;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private OrderOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "cancelled_at", updatable = false)
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", updatable = false)
    private String cancellationReason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<OrderItem> lines = new ArrayList<>();

    protected Order() {
    }

    public Order(UUID externalId, Agency agency, String requester, OrderOrigin origin) {
        this.externalId = externalId;
        this.agency = agency;
        this.requester = requester.trim();
        this.origin = origin;
        this.status = OrderStatus.CONFIRMED;
        this.createdAt = Instant.now();
    }

    public void addLine(Item item, int quantity) {
        lines.add(new OrderItem(this, item, quantity));
    }

    public Long getId() {
        return id;
    }

    public UUID getExternalId() {
        return externalId;
    }

    public Agency getAgency() {
        return agency;
    }

    public String getRequester() {
        return requester;
    }

    public OrderOrigin getOrigin() {
        return origin;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public List<OrderItem> getLines() {
        return Collections.unmodifiableList(lines);
    }
}

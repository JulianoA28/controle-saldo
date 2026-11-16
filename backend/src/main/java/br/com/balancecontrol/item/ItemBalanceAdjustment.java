package br.com.balancecontrol.item;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/** Immutable record of a manual balance change. */
@Entity
@Table(name = "item_balance_adjustment")
public class ItemBalanceAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false, updatable = false)
    private Long itemId;

    @Column(nullable = false, updatable = false)
    private int delta;

    @Column(name = "balance_after", nullable = false, updatable = false)
    private int balanceAfter;

    @Column(nullable = false, updatable = false)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ItemBalanceAdjustment() {
    }

    public ItemBalanceAdjustment(Long itemId, int delta, int balanceAfter, String reason) {
        this.itemId = itemId;
        this.delta = delta;
        this.balanceAfter = balanceAfter;
        this.reason = reason.trim();
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getItemId() {
        return itemId;
    }

    public int getDelta() {
        return delta;
    }

    public int getBalanceAfter() {
        return balanceAfter;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

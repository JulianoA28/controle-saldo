package br.com.balancecontrol.item;

import java.time.LocalDate;
import java.util.Locale;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;

/**
 * Item with an available balance.
 *
 * <p>The balance is {@code updatable = false}: Hibernate never writes it after insert. It only
 * changes through atomic SQL deltas in {@link ItemRepository} (order debit/credit, manual
 * adjustment), so a manager saving a stale description can never overwrite a balance that an
 * order changed in the meantime. {@link #version} protects the descriptive fields (R5).
 */
@Entity
@Table(name = "item")
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String unit;

    @Column(nullable = false, updatable = false)
    private int balance;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "registered_on", nullable = false, updatable = false)
    private LocalDate registeredOn;

    @Version
    @Column(nullable = false)
    private long version;

    protected Item() {
    }

    public Item(String code, String description, String unit, int initialBalance) {
        this.code = code.trim();
        this.description = description.trim();
        this.unit = normalizeUnit(unit);
        this.balance = initialBalance;
        this.active = true;
        this.registeredOn = LocalDate.now();
    }

    public void edit(String description, String unit, boolean active) {
        this.description = description.trim();
        this.unit = normalizeUnit(unit);
        this.active = active;
    }

    /** Same rule as the data load and the database CHECK: trimmed, upper case. */
    public static String normalizeUnit(String unit) {
        return unit.trim().toUpperCase(Locale.ROOT);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public String getUnit() {
        return unit;
    }

    public int getBalance() {
        return balance;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDate getRegisteredOn() {
        return registeredOn;
    }

    public long getVersion() {
        return version;
    }
}

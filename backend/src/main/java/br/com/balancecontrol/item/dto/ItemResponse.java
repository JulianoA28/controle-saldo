package br.com.balancecontrol.item.dto;

import br.com.balancecontrol.item.Item;
import java.time.LocalDate;

public record ItemResponse(Long id, String code, String description, String unit, int balance,
                           boolean active, LocalDate registeredOn, long version) {

    public static ItemResponse from(Item item) {
        return new ItemResponse(item.getId(), item.getCode(), item.getDescription(), item.getUnit(),
                item.getBalance(), item.isActive(), item.getRegisteredOn(), item.getVersion());
    }

    /** For responses built after an SQL balance change, when the entity's balance is stale. */
    public ItemResponse withBalance(int newBalance) {
        return new ItemResponse(id, code, description, unit, newBalance, active, registeredOn, version);
    }
}

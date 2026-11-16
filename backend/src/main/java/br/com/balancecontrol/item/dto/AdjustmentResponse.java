package br.com.balancecontrol.item.dto;

import br.com.balancecontrol.item.ItemBalanceAdjustment;
import java.time.Instant;

public record AdjustmentResponse(Long id, int delta, int balanceAfter, String reason, Instant createdAt) {

    public static AdjustmentResponse from(ItemBalanceAdjustment a) {
        return new AdjustmentResponse(a.getId(), a.getDelta(), a.getBalanceAfter(), a.getReason(), a.getCreatedAt());
    }
}

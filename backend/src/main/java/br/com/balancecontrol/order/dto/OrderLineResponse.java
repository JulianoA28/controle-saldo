package br.com.balancecontrol.order.dto;

import br.com.balancecontrol.order.OrderItem;

public record OrderLineResponse(Long itemId, String itemCode, String description, String unit, int quantity) {

    public static OrderLineResponse from(OrderItem line) {
        return new OrderLineResponse(line.getItem().getId(), line.getItem().getCode(),
                line.getItem().getDescription(), line.getItem().getUnit(), line.getQuantity());
    }
}

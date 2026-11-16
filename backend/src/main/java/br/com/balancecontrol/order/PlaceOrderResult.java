package br.com.balancecontrol.order;

import br.com.balancecontrol.order.dto.OrderResponse;

/** {@code created = false} means an idempotent replay: the order already existed. */
public record PlaceOrderResult(OrderResponse order, boolean created) {
}

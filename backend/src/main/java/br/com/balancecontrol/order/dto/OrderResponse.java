package br.com.balancecontrol.order.dto;

import br.com.balancecontrol.agency.AgencyResponse;
import br.com.balancecontrol.order.Order;
import br.com.balancecontrol.order.OrderOrigin;
import br.com.balancecontrol.order.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(Long id, UUID externalId, AgencyResponse agency, String requester,
                            OrderOrigin origin, OrderStatus status, Instant createdAt,
                            Instant cancelledAt, String cancellationReason, List<OrderLineResponse> items) {

    /** Must be called inside a transaction with agency and lines loaded. */
    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getExternalId(), AgencyResponse.from(order.getAgency()),
                order.getRequester(), order.getOrigin(), order.getStatus(), order.getCreatedAt(),
                order.getCancelledAt(), order.getCancellationReason(),
                order.getLines().stream().map(OrderLineResponse::from).toList());
    }
}

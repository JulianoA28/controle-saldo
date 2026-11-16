package br.com.balancecontrol.order.dto;

import br.com.balancecontrol.order.Order;
import br.com.balancecontrol.order.OrderOrigin;
import br.com.balancecontrol.order.OrderStatus;
import java.time.Instant;
import java.util.UUID;

/** Row of the order list: no lines, so a page is two queries (data + count). */
public record OrderSummaryResponse(Long id, UUID externalId, String agencyCode, String agencyAcronym,
                                   String requester, OrderOrigin origin, OrderStatus status,
                                   Instant createdAt, Instant cancelledAt) {

    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(order.getId(), order.getExternalId(), order.getAgency().getCode(),
                order.getAgency().getAcronym(), order.getRequester(), order.getOrigin(), order.getStatus(),
                order.getCreatedAt(), order.getCancelledAt());
    }
}

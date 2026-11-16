package br.com.balancecontrol.order;

import br.com.balancecontrol.common.web.PageResponse;
import br.com.balancecontrol.order.dto.CancelOrderRequest;
import br.com.balancecontrol.order.dto.OrderResponse;
import br.com.balancecontrol.order.dto.OrderSummaryResponse;
import br.com.balancecontrol.order.dto.PlaceOrderRequest;
import java.net.URI;
import java.util.UUID;
import javax.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    /** 201 when created; 200 with the same order when it is a resend of an existing external id (R6). */
    @PostMapping
    public ResponseEntity<OrderResponse> place(@Valid @RequestBody PlaceOrderRequest request) {
        PlaceOrderResult result = service.place(request);
        if (result.created()) {
            return ResponseEntity.created(URI.create("/orders/" + result.order().id())).body(result.order());
        }
        return ResponseEntity.ok(result.order());
    }

    @GetMapping
    public PageResponse<OrderSummaryResponse> list(@RequestParam(required = false) Long agencyId,
                                                   @RequestParam(required = false) OrderStatus status,
                                                   @RequestParam(required = false) UUID externalId,
                                                   Pageable pageable) {
        return service.list(agencyId, status, externalId, pageable);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable long id, @Valid @RequestBody(required = false) CancelOrderRequest request) {
        service.cancel(id, request == null ? null : request.reason());
        return service.get(id);
    }
}
